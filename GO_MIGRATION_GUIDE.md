# Node.js 到 Go 后端迁移指南

## 概述
本文档详细说明了将HomeMoney应用从Node.js/Express迁移到Go/Gin的具体方案，包括组件映射、潜在问题和API规范。

## 目录
1. [项目结构对比](#项目结构对比)
2. [组件迁移映射](#组件迁移映射)
3. [数据库迁移](#数据库迁移)
4. [API接口规范](#api接口规范)
5. [迁移风险与挑战](#迁移风险与挑战)
6. [实施建议](#实施建议)

## 项目结构对比

### 当前Node.js结构
```
server/
├── src/
│   ├── app.js              # 主应用入口
│   ├── db.js               # 数据库配置
│   ├── controllers/        # 控制器层
│   │   ├── expenseController.js
│   │   ├── memberController.js
│   │   ├── paymentController.js
│   │   ├── subscriptionController.js
│   │   ├── logController.js
│   ├── models/             # 数据模型
│   │   ├── expense.js
│   │   ├── member.js
│   │   ├── log.js
│   │   ├── subscriptionPlan.js
│   │   └── userSubscription.js
│   ├── routes/             # 路由配置
│   │   ├── api.js
│   │   ├── expense.js
│   │   └── ...
│   └── utils/              # 工具函数
└── package.json
```

### 目标Go结构
```
server-go/
├── cmd/
│   └── server/
│       └── main.go         # 主入口文件
├── internal/
│   ├── config/             # 配置管理
│   │   └── config.go
│   ├── handlers/           # 控制器层 (Go术语)
│   │   ├── expense.go
│   │   ├── member.go
│   │   ├── payment.go
│   │   ├── subscription.go
│   │   ├── log.go
│   ├── models/             # 数据模型
│   │   ├── expense.go
│   │   ├── member.go
│   │   ├── log.go
│   │   ├── subscription.go
│   │   └── user.go
│   ├── repository/         # 数据访问层
│   │   ├── expense.go
│   │   └── ...
│   ├── service/            # 业务逻辑层
│   │   ├── expense_service.go
│   │   └── ...
│   ├── routes/             # 路由配置
│   │   ├── router.go
│   │   └── handlers.go
│   └── middleware/         # 中间件
│       ├── cors.go
│       ├── logger.go
│       └── auth.go
└── pkg/                    # 公共包
    ├── database/           # 数据库连接
    │   └── database.go
    └── utils/
        └── response.go
```

## 组件迁移映射

### 1. 主应用文件

#### Node.js: app.js (130行)
```javascript
const express = require('express')
const cors = require('cors')
// ... 其他导入

const app = express()
const PORT = process.env.PORT || 3010

// 中间件配置
app.use(cors(corsOptions))
app.use(express.json())
app.use(express.urlencoded({ extended: true }))

// 路由配置
app.use('/api/expenses', require('./routes/expense'))
// ... 其他路由

// 错误处理
app.use((err, req, res, next) => {
  console.error('An error occurred:', err.stack)
  res.status(err.status || 500).json({
    success: false,
    error: 'Internal Server Error'
  })
})

// 服务器启动
const startServer = async () => {
  try {
    await syncDatabase()
    const server = http.createServer(app)
    server.listen(PORT, '0.0.0.0', () => {
      console.log(`✅ Server is running on port ${PORT}`)
    })
  } catch (error) {
    console.error('❌ Failed to start server:', error)
    process.exit(1)
  }
}
```

#### Go: main.go
```go
package main

import (
    "log"
    "net/http"
    "os"
    "os/signal"
    "syscall"
    "time"

    "github.com/gin-gonic/gin"
    "github.com/your-project/server-go/internal/config"
    "github.com/your-project/server-go/internal/handlers"
    "github.com/your-project/server-go/internal/middleware"
    "github.com/your-project/server-go/pkg/database"
)

func main() {
    // 初始化配置
    cfg, err := config.Load()
    if err != nil {
        log.Fatal("Failed to load config:", err)
    }

    // 初始化数据库
    db, err := database.InitDB(cfg.Database)
    if err != nil {
        log.Fatal("Failed to connect database:", err)
    }

    // 设置Gin模式
    if cfg.Environment == "production" {
        gin.SetMode(gin.ReleaseMode)
    }

    // 创建路由器
    r := gin.New()

    // 添加中间件
    r.Use(middleware.Logger())
    r.Use(middleware.CORS())
    r.Use(middleware.Recovery())

    // API路由
    api := r.Group("/api")
    {
        api.GET("/healthcheck", handlers.HealthCheck)
        api.GET("/expenses", handlers.GetExpenses)
        api.POST("/expenses", handlers.CreateExpense)
        api.DELETE("/expenses/:id", handlers.DeleteExpense)
        api.GET("/expenses/statistics", handlers.GetExpenseStatistics)
        
        api.POST("/members/get-or-create", handlers.GetOrCreateMember)
        api.GET("/members/:username", handlers.GetMemberInfo)
        api.PUT("/members/:id/status", handlers.UpdateMemberStatus)
        api.GET("/members/:username/subscriptions", handlers.GetMemberSubscriptions)
        
        api.POST("/payments/donate", handlers.Donate)
        api.POST("/payments/subscribe", handlers.SubscribePayment)
        
        api.GET("/subscriptions/plans", handlers.GetSubscriptionPlans)
        api.POST("/subscriptions", handlers.CreateSubscription)
        api.GET("/subscriptions/current/:username", handlers.GetCurrentSubscription)
        api.DELETE("/subscriptions/:username", handlers.CancelSubscription)
        
        api.POST("/logs", handlers.ReceiveLog)
        api.GET("/logs", handlers.GetLogsList)
        api.DELETE("/logs/clean", handlers.CleanLogs)
        api.GET("/logs/stats", handlers.GetLogStats)
    }

    // 基础路由
    r.GET("/", func(c *gin.Context) {
        c.JSON(http.StatusOK, gin.H{
            "message": "HomeMoney API Server",
            "status":  "running",
            "version": cfg.Version,
        })
    })

    // 优雅关闭
    srv := &http.Server{
        Addr:    ":" + cfg.Port,
        Handler: r,
    }

    go func() {
        if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
            log.Fatal("Failed to start server:", err)
        }
    }()

    // 等待中断信号
    quit := make(chan os.Signal, 1)
    signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
    <-quit

    log.Println("Shutting down server...")
    
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()

    if err := srv.Shutdown(ctx); err != nil {
        log.Fatal("Server forced to shutdown:", err)
    }

    log.Println("Server exited")
}
```

### 2. 数据模型迁移

#### Node.js Models
**消费模型 (models/expense.go)**:
```go
package models

import (
    "time"
    
    "github.com/google/uuid"
    "gorm.io/gorm"
)

// Expense 消费记录
type Expense struct {
    ID      uuid.UUID `json:"id" gorm:"type:uuid;primary_key"`
    Type    string    `json:"type" gorm:"type:varchar(50);not null"`
    Remark  *string   `json:"remark,omitempty" gorm:"type:text"`
    Amount  float64   `json:"amount" gorm:"type:decimal(10,2);not null"`
    Time    time.Time `json:"time" gorm:"type:datetime;not null"`
    
    // 时间戳
    CreatedAt time.Time `json:"created_at"`
    UpdatedAt time.Time `json:"updated_at"`
    DeletedAt time.Time `json:"deleted_at" gorm:"index"`
}

// TableName 指定表名
func (Expense) TableName() string {
    return "expenses"
}

// BeforeCreate GORM钩子
func (e *Expense) BeforeCreate(tx *gorm.DB) error {
    if e.ID == uuid.Nil {
        e.ID = uuid.New()
    }
    return nil
}

// ExpenseStats 消费统计
type ExpenseStats struct {
    TotalAmount   float64            `json:"total_amount"`
    Count         int                `json:"count"`
    AverageAmount float64            `json:"average_amount"`
    ByType        map[string]float64 `json:"by_type"`
    ByMonth       map[string]float64 `json:"by_month"`
}
```

**会员模型 (models/member.go)**:
```go
package models

import (
    "time"
    
    "github.com/google/uuid"
    "gorm.io/gorm"
)

// Member 会员
type Member struct {
    ID       uuid.UUID `json:"id" gorm:"type:uuid;primary_key"`
    Username string    `json:"username" gorm:"type:varchar(50);not null;uniqueIndex"`
    IsActive bool      `json:"is_active" gorm:"type:boolean;default:false"`
    
    // 时间戳
    CreatedAt time.Time `json:"created_at"`
    UpdatedAt time.Time `json:"updated_at"`
    DeletedAt time.Time `json:"deleted_at" gorm:"index"`
}

// TableName 指定表名
func (Member) TableName() string {
    return "members"
}

// BeforeCreate GORM钩子
func (m *Member) BeforeCreate(tx *gorm.DB) error {
    if m.ID == uuid.Nil {
        m.ID = uuid.New()
    }
    return nil
}
```

### 3. 控制器层迁移

#### Node.js: expenseController.js
```javascript
const { Expense, sequelize } = require('../db')
const dayjs = require('dayjs')
const { Op } = require('sequelize')

const getExpenses = async (req, res) => {
  try {
    const { page = 1, limit = 20, keyword, type, month, minAmount, maxAmount, sort = 'dateDesc' } = req.query
    const pageNum = parseInt(page, 10)
    const pageSize = parseInt(limit, 10)
    const offset = (pageNum - 1) * pageSize

    const where = {}
    if (type) where.type = type
    if (month) {
      const [year, monthNum] = month.split('-').map(Number)
      const startDate = new Date(year, monthNum - 1, 1)
      const endDate = new Date(year, monthNum, 0, 23, 59, 59, 999)
      where.time = { [Op.between]: [startDate, endDate] }
    }
    // ... 更多查询逻辑

    const { count, rows } = await Expense.findAndCountAll({
      where,
      limit: pageSize,
      offset: offset,
      order
    })

    res.json({
      data: rows,
      total: count,
      page: pageNum,
      limit: pageSize
    })
  } catch (err) {
    console.error('获取消费记录失败:', err)
    res.status(500).json({ error: '读取数据失败' })
  }
}
```

#### Go: handlers/expense.go
```go
package handlers

import (
    "net/http"
    "strconv"
    "time"

    "github.com/gin-gonic/gin"
    "github.com/your-project/server-go/internal/models"
    "github.com/your-project/server-go/internal/repository"
    "github.com/your-project/server-go/pkg/utils"
)

type ExpenseHandler struct {
    expenseRepo *repository.ExpenseRepository
}

func NewExpenseHandler(expenseRepo *repository.ExpenseRepository) *ExpenseHandler {
    return &ExpenseHandler{
        expenseRepo: expenseRepo,
    }
}

// GetExpenses 获取消费记录列表
func (h *ExpenseHandler) GetExpenses(c *gin.Context) {
    // 解析查询参数
    page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
    limit, _ := strconv.Atoi(c.DefaultQuery("limit", "20"))
    keyword := c.Query("keyword")
    expenseType := c.Query("type")
    month := c.Query("month")
    minAmountStr := c.Query("minAmount")
    maxAmountStr := c.Query("maxAmount")
    sort := c.DefaultQuery("sort", "dateDesc")

    // 参数验证
    if page < 1 {
        page = 1
    }
    if limit < 1 || limit > 100 {
        limit = 20
    }

    offset := (page - 1) * limit

    // 构建查询条件
    query := &repository.ExpenseQuery{
        Keyword:   keyword,
        Type:      expenseType,
        Month:     month,
        Limit:     limit,
        Offset:    offset,
        Sort:      sort,
    }

    // 处理金额范围
    if minAmountStr != "" {
        if amount, err := strconv.ParseFloat(minAmountStr, 64); err == nil {
            query.MinAmount = &amount
        }
    }

    if maxAmountStr != "" {
        if amount, err := strconv.ParseFloat(maxAmountStr, 64); err == nil {
            query.MaxAmount = &amount
        }
    }

    // 处理月份筛选
    if month != "" {
        if startDate, endDate, err := parseMonth(month); err == nil {
            query.StartDate = &startDate
            query.EndDate = &endDate
        }
    }

    // 执行查询
    expenses, total, err := h.expenseRepo.FindWithPagination(query)
    if err != nil {
        c.JSON(http.StatusInternalServerError, utils.ErrorResponse("读取数据失败", err.Error()))
        return
    }

    // 获取元数据
    meta, err := h.expenseRepo.GetMeta()
    if err != nil {
        // 不影响主要功能，记录警告
        meta = struct {
            UniqueTypes     []string `json:"unique_types"`
            AvailableMonths []string `json:"available_months"`
        }{}
    }

    c.JSON(http.StatusOK, utils.SuccessResponse(gin.H{
        "data":  expenses,
        "total": total,
        "page":  page,
        "limit": limit,
        "meta":  meta,
    }))
}

// CreateExpense 创建消费记录
func (h *ExpenseHandler) CreateExpense(c *gin.Context) {
    var expense models.Expense
    if err := c.ShouldBindJSON(&expense); err != nil {
        c.JSON(http.StatusBadRequest, utils.ErrorResponse("请求参数错误", err.Error()))
        return
    }

    // 验证必填字段
    if expense.Type == "" || expense.Amount <= 0 {
        c.JSON(http.StatusBadRequest, utils.ErrorResponse("消费类型和金额是必填项", ""))
        return
    }

    // 设置默认时间
    if expense.Time.IsZero() {
        expense.Time = time.Now()
    }

    // 保存记录
    if err := h.expenseRepo.Create(&expense); err != nil {
        c.JSON(http.StatusInternalServerError, utils.ErrorResponse("无法添加记录", err.Error()))
        return
    }

    c.JSON(http.StatusCreated, utils.SuccessResponse(expense))
}

// DeleteExpense 删除消费记录
func (h *ExpenseHandler) DeleteExpense(c *gin.Context) {
    id := c.Param("id")
    
    if err := h.expenseRepo.Delete(id); err != nil {
        c.JSON(http.StatusInternalServerError, utils.ErrorResponse("无法删除记录", err.Error()))
        return
    }

    c.Status(http.StatusNoContent)
}

// GetExpenseStatistics 获取消费统计
func (h *ExpenseHandler) GetExpenseStatistics(c *gin.Context) {
    // 构建查询条件（与GetExpenses相同逻辑）
    query := &repository.ExpenseQuery{
        // 从请求参数构建查询条件...
    }

    stats, err := h.expenseRepo.GetStatistics(query)
    if err != nil {
        c.JSON(http.StatusInternalServerError, utils.ErrorResponse("获取统计数据失败", err.Error()))
        return
    }

    c.JSON(http.StatusOK, utils.SuccessResponse(stats))
}

// parseMonth 解析月份参数 "YYYY-MM"
func parseMonth(month string) (time.Time, time.Time, error) {
    parsed, err := time.Parse("2006-01", month)
    if err != nil {
        return time.Time{}, time.Time{}, err
    }

    startDate := time.Date(parsed.Year(), parsed.Month(), 1, 0, 0, 0, 0, time.UTC)
    endDate := startDate.AddDate(0, 1, -1)
    endDate = time.Date(endDate.Year(), endDate.Month(), endDate.Day(), 23, 59, 59, 999999999, time.UTC)

    return startDate, endDate, nil
}
```

### 4. 数据访问层 (Repository Pattern)

#### Go: repository/expense.go
```go
package repository

import (
    "database/sql"
    "time"

    "github.com/your-project/server-go/internal/models"
    "gorm.io/gorm"
)

// ExpenseQuery 消费记录查询条件
type ExpenseQuery struct {
    Keyword    string     `form:"keyword"`
    Type       string     `form:"type"`
    Month      string     `form:"month"`
    MinAmount  *float64   `form:"minAmount"`
    MaxAmount  *float64   `form:"maxAmount"`
    StartDate  *time.Time `form:"startDate"`
    EndDate    *time.Time `form:"endDate"`
    Limit      int        `form:"limit,default=20"`
    Offset     int        `form:"offset,default=0"`
    Sort       string     `form:"sort,default=dateDesc"`
}

// ExpenseRepository 消费记录数据访问接口
type ExpenseRepository struct {
    db *gorm.DB
}

func NewExpenseRepository(db *gorm.DB) *ExpenseRepository {
    return &ExpenseRepository{db: db}
}

// FindWithPagination 分页查询消费记录
func (r *ExpenseRepository) FindWithPagination(query *ExpenseQuery) ([]models.Expense, int64, error) {
    var expenses []models.Expense
    var total int64

    // 构建查询
    db := r.db.Model(&models.Expense{})

    // 应用筛选条件
    if query.Keyword != "" {
        db = db.Where("type LIKE ? OR remark LIKE ?", 
            "%"+query.Keyword+"%", "%"+query.Keyword+"%")
    }
    if query.Type != "" {
        db = db.Where("type = ?", query.Type)
    }
    if query.StartDate != nil && query.EndDate != nil {
        db = db.Where("time BETWEEN ? AND ?", *query.StartDate, *query.EndDate)
    }
    if query.MinAmount != nil {
        db = db.Where("amount >= ?", *query.MinAmount)
    }
    if query.MaxAmount != nil {
        db = db.Where("amount <= ?", *query.MaxAmount)
    }

    // 获取总数
    if err := db.Count(&total).Error; err != nil {
        return nil, 0, err
    }

    // 应用排序
    switch query.Sort {
    case "dateAsc":
        db = db.Order("time ASC")
    case "dateDesc":
        db = db.Order("time DESC")
    case "amountAsc":
        db = db.Order("amount ASC")
    case "amountDesc":
        db = db.Order("amount DESC")
    default:
        db = db.Order("time DESC")
    }

    // 分页
    db = db.Offset(query.Offset).Limit(query.Limit)

    // 执行查询
    if err := db.Find(&expenses).Error; err != nil {
        return nil, 0, err
    }

    return expenses, total, nil
}

// Create 创建消费记录
func (r *ExpenseRepository) Create(expense *models.Expense) error {
    return r.db.Create(expense).Error
}

// Delete 删除消费记录
func (r *ExpenseRepository) Delete(id string) error {
    return r.db.Delete(&models.Expense{}, "id = ?", id).Error
}

// GetStatistics 获取统计数据
func (r *ExpenseRepository) GetStatistics(query *ExpenseQuery) (*models.ExpenseStats, error) {
    db := r.db.Model(&models.Expense{})

    // 应用筛选条件
    if query.Type != "" {
        db = db.Where("type = ?", query.Type)
    }
    if query.StartDate != nil && query.EndDate != nil {
        db = db.Where("time BETWEEN ? AND ?", *query.StartDate, *query.EndDate)
    }
    if query.MinAmount != nil {
        db = db.Where("amount >= ?", *query.MinAmount)
    }
    if query.MaxAmount != nil {
        db = db.Where("amount <= ?", *query.MaxAmount)
    }

    stats := &models.ExpenseStats{
        ByType:  make(map[string]float64),
        ByMonth: make(map[string]float64),
    }

    // 获取总金额和数量
    var totalAmount sql.NullFloat64
    var count int64
    
    if err := db.Select("COALESCE(SUM(amount), 0)").Row().Scan(&totalAmount); err != nil {
        return nil, err
    }
    stats.TotalAmount = totalAmount.Float64

    if err := db.Count(&count).Error; err != nil {
        return nil, err
    }
    stats.Count = int(count)
    
    if count > 0 {
        stats.AverageAmount = stats.TotalAmount / float64(count)
    }

    // 按类型统计
    typeStats := []struct {
        Type   string  `json:"type"`
        Amount float64 `json:"amount"`
    }{}
    
    if err := db.Select("type, SUM(amount) as amount").Group("type").Scan(&typeStats).Error; err != nil {
        return nil, err
    }
    
    for _, stat := range typeStats {
        stats.ByType[stat.Type] = stat.Amount
    }

    // 按月份统计
    monthStats := []struct {
        Month  string  `json:"month"`
        Amount float64 `json:"amount"`
    }{}
    
    if err := db.Raw("SELECT strftime('%Y-%m', time) as month, SUM(amount) as amount FROM expenses GROUP BY month ORDER BY month DESC").Scan(&monthStats).Error; err != nil {
        return nil, err
    }
    
    for _, stat := range monthStats {
        stats.ByMonth[stat.Month] = stat.Amount
    }

    return stats, nil
}

// GetMeta 获取元数据（唯一类型和可用月份）
func (r *ExpenseRepository) GetMeta() (interface{}, error) {
    // 获取唯一类型
    var types []string
    if err := r.db.Model(&models.Expense{}).Distinct().Pluck("type", &types).Error; err != nil {
        return nil, err
    }

    // 获取可用月份
    var months []string
    if err := r.db.Raw("SELECT DISTINCT strftime('%Y-%m', time) as month FROM expenses ORDER BY month DESC").Scan(&months).Error; err != nil {
        return nil, err
    }

    return struct {
        UniqueTypes     []string `json:"unique_types"`
        AvailableMonths []string `json:"available_months"`
    }{
        UniqueTypes:     types,
        AvailableMonths: months,
    }, nil
}
```

### 5. 数据库配置

#### Node.js: db.js
```javascript
const Sequelize = require('sequelize')
const path = require('path')
const sequelize = new Sequelize({ 
  dialect: 'sqlite', 
  storage: path.join(__dirname, '../database.sqlite') 
})

// 导入模型
const Expense = require('./models/expense')(sequelize)

// 定义关系
Member.hasMany(UserSubscription, { foreignKey: 'memberId' })
UserSubscription.belongsTo(Member, { foreignKey: 'memberId' })

module.exports = { sequelize, Expense, /* ... */ }
```

#### Go: pkg/database/database.go
```go
package database

import (
    "fmt"
    
    "github.com/your-project/server-go/internal/models"
    "gorm.io/driver/sqlite"
    "gorm.io/gorm"
    "gorm.io/gorm/logger"
)

// Config 数据库配置
type Config struct {
    Host     string
    Port     string
    User     string
    Password string
    Database string
    SSLMode  string
}

// InitDB 初始化数据库连接
func InitDB(cfg *Config) (*gorm.DB, error) {
    dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=True&loc=Local",
        cfg.User, cfg.Password, cfg.Host, cfg.Port, cfg.Database)
    
    // SQLite配置示例
    if cfg.Host == "sqlite" {
        dsn = cfg.Database
    }

    db, err := gorm.Open(sqlite.Open(dsn), &gorm.Config{
        Logger: logger.Default.LogMode(logger.Info),
    })
    if err != nil {
        return nil, fmt.Errorf("failed to connect database: %w", err)
    }

    // 自动迁移
    if err := AutoMigrate(db); err != nil {
        return nil, fmt.Errorf("failed to migrate database: %w", err)
    }

    return db, nil
}

// AutoMigrate 自动迁移数据库结构
func AutoMigrate(db *gorm.DB) error {
    return db.AutoMigrate(
        &models.Expense{},
        &models.Member{},
        &models.SubscriptionPlan{},
        &models.UserSubscription{},
        &models.OperationLog{},
    )
}

// CloseDB 关闭数据库连接
func CloseDB(db *gorm.DB) error {
    sqlDB, err := db.DB()
    if err != nil {
        return err
    }
    return sqlDB.Close()
}
```

### 6. 中间件实现

#### Go: middleware/cors.go
```go
package middleware

import (
    "github.com/gin-gonic/gin"
    "github.com/your-project/server-go/pkg/utils"
)

// CORS 中间件
func CORS() gin.HandlerFunc {
    return func(c *gin.Context) {
        method := c.Request.Method
        origin := c.Request.Header.Get("Origin")
        
        c.Header("Access-Control-Allow-Origin", origin)
        c.Header("Access-Control-Allow-Credentials", "true")
        c.Header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
        c.Header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")

        if method == "OPTIONS" {
            c.AbortWithStatus(204)
            return
        }

        c.Next()
    }
}

// Logger 日志中间件
func Logger() gin.HandlerFunc {
    return gin.LoggerWithFormatter(func(param gin.LogFormatterParams) string {
        return utils.LogFormat{
            Time:        param.TimeStamp.Format("2006-01-02 15:04:05"),
            StatusCode:  param.StatusCode,
            Latency:     param.Latency,
            ClientIP:    param.ClientIP,
            Method:      param.Method,
            Path:        param.Path,
            ErrorMessage: param.ErrorMessage,
        }.String()
    })
}

// Recovery 恢复中间件
func Recovery() gin.HandlerFunc {
    return gin.CustomRecovery(func(c *gin.Context, recovered interface{}) {
        utils.ErrorResponse(c, "内部服务器错误", recovered.(error).Error())
        c.Abort()
    })
}
```

### 7. 工具函数

#### Go: pkg/utils/response.go
```go
package utils

import (
    "net/http"

    "github.com/gin-gonic/gin"
)

// SuccessResponse 成功响应
func SuccessResponse(data interface{}) gin.H {
    return gin.H{
        "success": true,
        "data":    data,
    }
}

// ErrorResponse 错误响应
func ErrorResponse(message, error string) gin.H {
    return gin.H{
        "success": false,
        "error":   message,
        "details": error,
    }
}

// PaginatedResponse 分页响应
func PaginatedResponse(data interface{}, total int64, page, limit int) gin.H {
    return gin.H{
        "success": true,
        "data":    data,
        "total":   total,
        "page":    page,
        "limit":   limit,
        "pages":   (total + int64(limit) - 1) / int64(limit), // 向上取整
    }
}

// ErrorResponseWithStatus 带状态码的错误响应
func ErrorResponseWithStatus(c *gin.Context, message, error string, statusCode int) {
    c.JSON(statusCode, ErrorResponse(message, error))
}

// LogFormat 日志格式
type LogFormat struct {
    Time         string
    StatusCode   int
    Latency      string
    ClientIP     string
    Method       string
    Path         string
    ErrorMessage string
}

func (f LogFormat) String() string {
    if f.ErrorMessage == "" {
        return f.Time + " [" + f.Method + "] " + f.Path + 
               " - " + f.ClientIP + " - " + f.StatusCode + 
               " - " + f.Latency + "\n"
    }
    return f.Time + " [" + f.Method + "] " + f.Path + 
           " - " + f.ClientIP + " - " + f.StatusCode + 
           " - " + f.Latency + " - ERROR: " + f.ErrorMessage + "\n"
}
```

## API接口规范

### 1. 消费记录接口

#### GET /api/expenses
**功能**: 获取消费记录列表（支持分页和筛选）

**请求参数**:
```json
{
  "page": 1,
  "limit": 20,
  "keyword": "餐饮",
  "type": "food",
  "month": "2024-01",
  "minAmount": 10.0,
  "maxAmount": 100.0,
  "sort": "dateDesc"
}
```

**响应数据**:
```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "type": "餐饮",
      "remark": "午餐",
      "amount": 25.50,
      "time": "2024-01-15T12:30:00Z",
      "created_at": "2024-01-15T12:30:00Z",
      "updated_at": "2024-01-15T12:30:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "limit": 20,
  "meta": {
    "unique_types": ["餐饮", "交通", "购物"],
    "available_months": ["2024-01", "2023-12"]
  }
}
```

#### POST /api/expenses
**功能**: 创建消费记录

**请求体**:
```json
{
  "type": "餐饮",
  "remark": "午餐",
  "amount": 25.50,
  "time": "2024-01-15T12:30:00Z"
}
```

**响应数据**:
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "type": "餐饮",
    "remark": "午餐",
    "amount": 25.50,
    "time": "2024-01-15T12:30:00Z",
    "created_at": "2024-01-15T12:30:00Z",
    "updated_at": "2024-01-15T12:30:00Z"
  }
}
```


### 3. 会员管理接口

#### POST /api/members/get-or-create
**功能**: 创建或获取会员

#### GET /api/members/:username
**功能**: 获取会员信息

### 4. 支付接口

#### POST /api/payments/donate
**功能**: 处理捐赠支付

#### POST /api/payments/subscribe
**功能**: 处理订阅支付

### 5. 订阅管理接口

#### GET /api/subscriptions/plans
**功能**: 获取订阅计划

#### GET /api/subscriptions/current/:username
**功能**: 获取用户当前订阅

### 6. 日志管理接口

#### POST /api/logs
**功能**: 接收前端日志

#### GET /api/logs
**功能**: 获取日志列表（管理员功能）

#### DELETE /api/logs/clean
**功能**: 清理过期日志

## 迁移风险与挑战

### 1. 数据库兼容性风险

**SQLite特性依赖**:
- 当前Node.js代码大量使用SQLite特有函数如`strftime`
- **风险**: Go的SQLite驱动可能不完全支持所有SQLite特性
- **解决方案**: 
  - 使用成熟的SQLite驱动如 `mattn/go-sqlite3` 或 `modernc.org/sqlite`
  - 对于复杂查询，考虑使用GORM的原生查询

**模型关系**:
- Sequelize的关联模型和钩子在Go中的实现方式不同
- **风险**: 模型关系可能需要重构
- **解决方案**: 使用GORM的关联标签重新定义关系

### 2. JSON序列化差异

**数据类型转换**:
```javascript
// Node.js: 字符串和数字容易转换
const amount = parseFloat("25.50") // 25.50
const isRepaid = isRepaid === 'true' // true/false
```

```go
// Go: 需要显式类型转换
amount, _ := strconv.ParseFloat("25.50", 64)
isRepaid := request.IsRepaid == "true"
```

**风险**: 数据类型转换可能引入bug
**解决方案**: 
- 实现强类型的请求/响应结构体
- 添加完善的输入验证
- 使用Go的类型系统优势

### 3. 时间处理差异

**JavaScript vs Go时间处理**:
```javascript
// Node.js: dayjs
const newDate = dayjs(date).toDate()
const nextMonth = dayjs().add(1, 'month')
```

```go
// Go: time包
newDate := time.Now()
if date != "" {
    if parsedDate, err := time.Parse("2006-01-02", date); err == nil {
        newDate = parsedDate
    }
}
nextMonth := time.Now().AddDate(0, 1, 0)
```

**风险**: 时区和日期格式处理可能不一致
**解决方案**: 
- 统一使用UTC时间进行存储
- 在API层处理时区转换
- 定义标准的日期时间格式

### 4. 错误处理差异

**异步错误处理**:
```javascript
// Node.js: Promise/async-await
try {
  const result = await someAsyncOperation()
  res.json(result)
} catch (error) {
  console.error('Error:', error)
  res.status(500).json({ error: error.message })
}
```

```go
// Go: 显式错误返回
result, err := someOperation()
if err != nil {
    log.Error("Error:", err)
    c.JSON(500, utils.ErrorResponse("操作失败", err.Error()))
    return
}
c.JSON(200, utils.SuccessResponse(result))
```

### 5. 包依赖管理

**依赖数量对比**:
- Node.js: ~15个主要依赖（express, sequelize, cors, axios等）
- Go: ~8个主要依赖（gin, gorm, go-sqlite3等）

**风险**: 依赖数量增加维护成本
**解决方案**: 
- 使用Go modules进行依赖管理
- 定期更新和审查依赖
- 考虑使用Go标准库替代第三方包

### 6. 性能考虑

**并发处理**:
```javascript
// Node.js: 事件循环，单线程
app.get('/api/expenses', async (req, res) => {
  // I/O密集型操作适合Node.js
  const expenses = await Expense.findAll()
  res.json(expenses)
})
```

```go
// Go: goroutine，并发处理
func GetExpenses(c *gin.Context) {
    // 每个请求在新goroutine中处理
    go func() {
        expenses, err := expenseRepo.FindAll()
        // 处理逻辑
    }()
}
```

**内存使用**:
- Go应用通常比Node.js应用使用更少内存
- 需要注意goroutine泄漏

### 7. 开发复杂度

**代码量增长**:
- Node.js: 约2000行代码
- Go: 预计3000-4000行代码（增加注释和类型定义）

**风险**: 开发时间增加约50%
**解决方案**: 
- 采用渐进式迁移
- 分模块逐步替换
- 保持现有API接口不变

## 实施建议

### 1. 迁移策略

**阶段1: 核心基础设施**
- 搭建Go项目结构
- 配置数据库连接
- 实现基础中间件
- 创建核心工具函数

**阶段2: 数据模型**
- 迁移所有数据模型
- 实现数据访问层
- 确保数据库关系正确

**阶段3: API实现**
- 按模块逐步迁移控制器
- 保持API接口兼容性
- 实现完整的错误处理

**阶段4: 测试和优化**
- 单元测试覆盖率
- 性能测试和优化
- 文档完善

### 2. 技术选型建议

**Web框架**: Gin
- 性能优秀
- 学习曲线平缓
- 社区活跃

**ORM**: GORM
- 功能完整
- 支持多种数据库
- 迁移工具完善

**数据库**: SQLite (保持现有)
- 兼容性好
- 零配置
- 适合中小型应用

### 3. 项目结构建议

```
server-go/
├── cmd/
│   └── server/main.go
├── internal/
│   ├── config/          # 配置管理
│   ├── handlers/        # HTTP处理层
│   ├── models/          # 数据模型
│   ├── repository/      # 数据访问层
│   ├── service/         # 业务逻辑层
│   ├── routes/          # 路由配置
│   └── middleware/      # 中间件
├── pkg/                 # 公共包
└── scripts/             # 脚本文件
```

### 4. 关键注意事项

**保持API兼容性**:
- 确保所有现有API端点正常工作
- 保持响应格式一致
- 渐进式替换而非一次性迁移

**错误处理**:
- 实现统一的错误响应格式
- 记录详细错误日志
- 优雅降级机制

**性能监控**:
- 添加性能指标收集
- 监控内存和CPU使用
- 数据库连接池优化

**测试策略**:
- 现有前端应用继续使用
- API测试覆盖率100%
- 回归测试确保功能正常

### 5. 预期收益

**性能提升**:
- CPU使用率降低20-30%
- 内存使用降低40-50%
- 响应时间提升15-25%

**开发效率**:
- 编译时错误检查
- 更好的IDE支持
- 类型安全

**运维优势**:
- 更小的部署包
- 更好的部署效率
- 监控和调试工具更丰富

## 结论

从Node.js迁移到Go是一个有价值的投资，虽然会增加初期开发成本，但长期来看能带来性能、维护性和开发效率的显著提升。关键是要采用渐进式迁移策略，确保业务连续性，同时充分利用Go语言的优势。

建议优先迁移核心的CRUD操作模块，然后逐步处理复杂的业务逻辑和第三方集成。在迁移过程中，保持API接口的兼容性是关键，这样可以确保前端应用和现有用户不受影响。