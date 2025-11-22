package models

// DonationRecord 捐赠记录模型
type DonationRecord struct {
	ID        string  `json:"id"`
	Username  string  `json:"username"`
	Amount    float64 `json:"amount"`
	Timestamp string  `json:"timestamp"`
	OrderID   string  `json:"orderId"`
	Status    string  `json:"status"`
}