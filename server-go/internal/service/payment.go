package service

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"homemoney/internal/models"
	"homemoney/internal/repository"
)

// PaymentService 支付服务
type PaymentService struct {
	subscriptionPlanRepo *repository.SubscriptionPlanRepository
	memberRepo           *repository.MemberRepository
}

// NewPaymentService 创建支付服务
func NewPaymentService(
	subscriptionPlanRepo *repository.SubscriptionPlanRepository,
	memberRepo *repository.MemberRepository,
) *PaymentService {
	return &PaymentService{
		subscriptionPlanRepo: subscriptionPlanRepo,
		memberRepo:           memberRepo,
	}
}

// DonationRequest 捐赠请求参数
type DonationRequest struct {
	Username string  `json:"username" binding:"required"`
	Amount   float64 `json:"amount" binding:"required,gt=0"`
}

// SubscribeRequest 订阅支付请求参数
type SubscribeRequest struct {
	Username string `json:"username" binding:"required"`
	PlanID   string `json:"planId" binding:"required"`
}

// PaymentData 第三方支付请求数据
type PaymentData struct {
	Username       string  `json:"username"`
	Amount         float64 `json:"amount"`
	ThirdPartyID   string  `json:"thirdPartyId"`
	ThirdPartyName string  `json:"thirdPartyName"`
	Description    string  `json:"description"`
}

// PaymentResponse 支付响应
type PaymentResponse struct {
	Success bool        `json:"success"`
	Data    interface{} `json:"data"`
	Error   string      `json:"error,omitempty"`
}

// ThirdPartyPaymentResponse 第三方支付API响应
type ThirdPartyPaymentResponse struct {
	OrderID string `json:"orderId"`
}

// Donate 处理捐赠
func (s *PaymentService) Donate(username string, amount float64) (*PaymentResponse, error) {
	// 验证金额
	if amount <= 0 {
		return &PaymentResponse{
			Success: false,
			Error:   "金额必须大于0",
		}, fmt.Errorf("金额必须大于0")
	}

	// 构建支付数据
	currentTime := time.Now().Format("2006-01-02 15:04:05")
	paymentData := PaymentData{
		Username:       username,
		Amount:         amount,
		ThirdPartyID:   getEnv("THIRD_PARTY_ID", "HomeMoney"),
		ThirdPartyName: getEnv("THIRD_PARTY_NAME", "家庭财务管理应用"),
		Description: fmt.Sprintf(`
			Donation by user %s的捐赠
			金额 Amount：%f
			时间 Time：%s
			若有问题请联系我们：
			If you have any questions, please contact us: 
			https://wj.qq.com/s2/24109109/3572/
		`, username, amount, currentTime),
	}

	// 调用第三方支付API
	response, err := s.callThirdPartyPaymentAPI(paymentData)
	if err != nil {
		return &PaymentResponse{
			Success: false,
			Error:   "支付处理失败，请稍后重试",
		}, err
	}

	// 记录捐赠日志
	fmt.Printf("用户%s捐赠了%f元\n", username, amount)

	// 保存捐赠记录
	donationRecord := models.DonationRecord{
		ID:        fmt.Sprintf("%d", time.Now().UnixNano()),
		Username:  username,
		Amount:    amount,
		Timestamp: currentTime,
		OrderID:   response.OrderID,
		Status:    "success",
	}

	// 异步保存捐赠记录
	go s.saveDonationRecord(donationRecord)

	return &PaymentResponse{
		Success: true,
		Data:    response,
	}, nil
}

// SubscribePayment 处理订阅支付
func (s *PaymentService) SubscribePayment(username string, planID string) (*PaymentResponse, error) {
	// 获取订阅计划
	plan, err := s.subscriptionPlanRepo.FindByPeriod(planID)
	if err != nil || plan == nil || !plan.IsActive {
		return &PaymentResponse{
			Success: false,
			Error:   "订阅计划不存在或已停用",
		}, fmt.Errorf("订阅计划不存在或已停用")
	}

	// 构建支付数据
	currentTime := time.Now().Format("2006-01-02 15:04:05")
	periodText := "月度"
	if plan.Period == "quarterly" {
		periodText = "季度"
	} else if plan.Period == "yearly" {
		periodText = "年度"
	}

	paymentData := PaymentData{
		Username:       username,
		Amount:         plan.Price,
		ThirdPartyID:   getEnv("THIRD_PARTY_ID", "HomeMoney"),
		ThirdPartyName: getEnv("THIRD_PARTY_NAME", "家庭财务管理应用"),
		Description: fmt.Sprintf(`
			会员订阅支付 - %s
			User %s Membership Subscription
			金额 Amount：%f
			订阅周期 Subscription Period：%s
			时间 Time：%s
			订阅计划ID Plan ID：%s
		`, plan.Name, username, plan.Price, periodText, currentTime, planID),
	}

	// 调用第三方支付API
	response, err := s.callThirdPartyPaymentAPI(paymentData)
	if err != nil {
		return &PaymentResponse{
			Success: false,
			Error:   "支付处理失败，请稍后重试",
		}, err
	}

	// 生成订单ID
	orderID := response.OrderID
	if orderID == "" {
		orderID = fmt.Sprintf("SUB_%s_%d", username, time.Now().UnixNano())
	}

	// 记录订阅支付日志
	fmt.Printf("用户%s订阅了%s，金额%f元\n", username, plan.Name, plan.Price)

	// 返回支付结果，包含订单ID供后续创建订阅使用
	return &PaymentResponse{
		Success: true,
		Data: map[string]interface{}{
			"orderId":  orderID,
			"planId":   plan.ID,
			"planName": plan.Name,
			"price":    plan.Price,
		},
	}, nil
}

// callThirdPartyPaymentAPI 调用第三方支付API
func (s *PaymentService) callThirdPartyPaymentAPI(paymentData PaymentData) (*ThirdPartyPaymentResponse, error) {
	apiURL := getEnv("THIRD_PARTY_PAYMENT_API", "http://192.168.0.197:3200/api/third-party/payments")

	// 转换为JSON
	jsonData, err := json.Marshal(paymentData)
	if err != nil {
		return nil, err
	}

	// 创建请求
	req, err := http.NewRequest("POST", apiURL, bytes.NewBuffer(jsonData))
	if err != nil {
		return nil, err
	}

	req.Header.Set("Content-Type", "application/json")

	// 由于这是模拟环境，我们直接返回一个模拟的响应
	// 在实际环境中，这里应该发送HTTP请求到第三方支付API
	return &ThirdPartyPaymentResponse{
		OrderID: fmt.Sprintf("ORDER_%d", time.Now().UnixNano()),
	}, nil
}

// saveDonationRecord 保存捐赠记录到JSON文件
func (s *PaymentService) saveDonationRecord(record models.DonationRecord) {
	// 确保数据目录存在
	dataDir := filepath.Join(".", "data")
	if err := os.MkdirAll(dataDir, 0755); err != nil {
		fmt.Printf("创建数据目录失败: %v\n", err)
		return
	}

	// 捐赠记录文件路径
	donationFilePath := filepath.Join(dataDir, "donation_records.json")

	// 将记录转换为JSON字符串并添加换行符
	recordJSON, err := json.Marshal(record)
	if err != nil {
		fmt.Printf("序列化捐赠记录失败: %v\n", err)
		return
	}

	recordLine := string(recordJSON) + "\n"

	// 追加记录到文件，每个记录占一行
	// 使用 os.OpenFile 以追加模式打开文件
	file, err := os.OpenFile(donationFilePath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		fmt.Printf("打开捐赠记录文件失败: %v\n", err)
		return
	}
	defer file.Close()

	if _, err := file.WriteString(recordLine); err != nil {
		fmt.Printf("保存捐赠记录失败: %v\n", err)
		return
	}

	fmt.Printf("捐赠记录已保存到文件: %s\n", donationFilePath)
	// 错误处理已在各步骤中完成
}

// getEnv 获取环境变量，如果不存在则返回默认值
func getEnv(key, defaultValue string) string {
	if value, exists := os.LookupEnv(key); exists {
		return value
	}
	return defaultValue
}

// try-catch 模拟函数
func try(f func(), catch func(error)) {
	defer func() {
		if r := recover(); r != nil {
			var err error
			switch x := r.(type) {
			case string:
				err = fmt.Errorf(x)
			case error:
				err = x
			default:
				err = fmt.Errorf("unknown error: %v", x)
			}
			catch(err)
		}
	}()
	f()
}
