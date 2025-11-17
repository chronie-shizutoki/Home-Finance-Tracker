package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"homemoney/pkg/database"
	"homemoney/internal/repository"
	"homemoney/internal/routes"
)




func main() {
	// 记录服务器启动时间
	startTime := time.Now()

	// 初始化日志
	log.SetFlags(log.LstdFlags | log.Lshortfile)

	// 加载配置
	config := routes.GetDefaultConfig()
	if port := os.Getenv("PORT"); port != "" {
		config.Port = port
	}

	// 初始化数据库
	db, err := database.InitDB("../server/database.sqlite")
	if err != nil {
		log.Fatalf("数据库初始化失败: %v", err)
	}
	defer db.Close()

	// 创建Repository实例
	expenseRepo := repository.NewExpenseRepository(db.GetDB())
	
	// 创建会员相关的Repository实例
	memberRepo := repository.NewMemberRepository(db.GetDB())
	planRepo := repository.NewSubscriptionPlanRepository(db.GetDB())
	subscriptionRepo := repository.NewUserSubscriptionRepository(db.GetDB())

	// 设置Gin模式
	if os.Getenv("GIN_MODE") == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	// 创建路由引擎
	router := gin.New()

	// 添加中间件
	router.Use(
		// 恢复Panic
		gin.CustomRecovery(func(c *gin.Context, recovered interface{}) {
			log.Printf("Panic recovered: %v", recovered)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "内部服务器错误",
				"code":    "INTERNAL_ERROR",
			})
		}),
		// 请求日志
		gin.Logger(),
	)

	// 设置系统相关的路由（健康检查和API文档）
	routes.SetupHealthRoutes(router, startTime)
	routes.SetupHelpRoutes(router)

	// 设置API路由
	routes.SetupExpenseRoutes(router, expenseRepo)
	
	// 设置会员相关的API路由 - 对应JS版本的memberRoutes
	routes.SetupMemberRoutes(router, memberRepo, planRepo, subscriptionRepo)

	// 创建HTTP服务器
	srv := &http.Server{
		Addr:         config.Host + ":" + config.Port,
		Handler:      router,
		ReadTimeout:  config.ReadTimeout,
		WriteTimeout: config.WriteTimeout,
		IdleTimeout:  config.IdleTimeout,
	}

	// 启动服务器
	go func() {
		log.Printf("服务器启动在端口 %s", config.Port)
		log.Printf("API文档: http://localhost:%s/api", config.Port)
		
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("服务器启动失败: %v", err)
		}
	}()

	// 等待中断信号优雅关闭服务器
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("正在关闭服务器...")

	// 优雅关闭服务器
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Fatalf("服务器强制关闭: %v", err)
	}

	log.Println("服务器已退出")
}

// 在main函数之后添加一些辅助函数和配置验证
// validateConfig 验证服务器配置，目前直接返回 nil，可后续扩展
func validateConfig() error {
	// 这里可以添加配置验证逻辑
	return nil
}

// SetupLogLevel 设置日志级别
func SetupLogLevel(level string) {
	switch level {
	case "debug":
		gin.SetMode(gin.DebugMode)
	case "release":
		gin.SetMode(gin.ReleaseMode)
	default:
		gin.SetMode(gin.TestMode)
	}
}
