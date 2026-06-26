package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"homemoney/internal/repository"
	"homemoney/internal/routes"
	"homemoney/internal/service"
	"homemoney/pkg/database"

	"github.com/gin-gonic/gin"
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
	db, err := database.InitDB("./database.sqlite")
	if err != nil {
		log.Fatalf("数据库初始化失败: %v", err)
	}
	defer func() { _ = db.Close() }()

	// 创建Repository实例
	expenseRepo := repository.NewExpenseRepository(db.GetDB())

	// 创建会员相关的Repository实例
	memberRepo := repository.NewMemberRepository(db.GetDB())

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
		// CORS 中间件 - 与JS版本一致
		func(c *gin.Context) {
			origin := c.Request.Header.Get("Origin")
			allowedOrigins := map[string]bool{
				"http://localhost:5173":  true,
				"http://0.0.0.0:5173":   true,
			}
			if allowedOrigins[origin] {
				c.Header("Access-Control-Allow-Origin", origin)
			} else {
				c.Header("Access-Control-Allow-Origin", "http://localhost:5173")
			}
			c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			c.Header("Access-Control-Allow-Headers", "Content-Type, Authorization")
			c.Header("Access-Control-Allow-Credentials", "true")
			if c.Request.Method == "OPTIONS" {
				c.AbortWithStatus(200)
				return
			}
			c.Next()
		},
	)

	// 增加JSON解析的大小限制，与JS版本一致（10MB）
	router.MaxMultipartMemory = 10 << 20 // 10MB

	// 设置系统相关的路由（健康检查和API文档）
	routes.SetupHealthRoutes(router, startTime)
	routes.SetupHelpRoutes(router)

	// 设置API路由
	routes.SetupExpenseRoutes(router, expenseRepo)

	// 设置会员相关的API路由 - 对应JS版本的memberRoutes
	routes.SetupMemberRoutes(router, memberRepo)

	// 设置错误报告路由
	errorReportRepo := repository.NewErrorReportRepository(db.GetDB())
	routes.SetupErrorReportRoutes(router, errorReportRepo)

	// 设置导出/导入路由
	routes.SetupExportRoutes(router, expenseRepo)

	// 初始化服务实例
	// 创建JSON文件服务实例
	jsonFileService := service.NewJsonFileService()
	// 创建日志服务实例
	logService := service.NewLogService(db.GetDB())

	// 注册路由
	routes.SetupJsonFileRoutes(router.Group("/api"), jsonFileService)
	routes.SetupLogRoutes(router.Group("/api"), logService)

	// 提供前端静态文件服务（当client/dist存在时自动启用）
	distPath := filepath.Join(".", "client", "dist")
	if _, err := os.Stat(filepath.Join(distPath, "index.html")); err == nil {
		log.Printf("Serving static files from: %s", distPath)
		router.Static("/assets", filepath.Join(distPath, "assets"))
		router.StaticFile("/favicon.ico", filepath.Join(distPath, "favicon.ico"))
		router.StaticFile("/photo.html", filepath.Join(distPath, "photo.html"))
		router.GET("/", func(c *gin.Context) {
			c.File(filepath.Join(distPath, "index.html"))
		})
		// SPA fallback - 只对页面路由返回index.html，静态资源请求返回404
		// 避免JS/CSS等静态资源请求被错误返回index.html导致MIME类型错误
		router.NoRoute(func(c *gin.Context) {
			path := c.Request.URL.Path
			// API路径返回404（API路由应已注册）
			// 带扩展名的静态资源请求（.js/.css/.png等）返回404而非index.html
			if strings.HasPrefix(path, "/api/") || isStaticAsset(path) {
				c.Status(http.StatusNotFound)
				return
			}
			// 页面路由返回index.html实现SPA
			c.File(filepath.Join(distPath, "index.html"))
		})
	}

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

// isStaticAsset 判断请求路径是否为静态资源请求
// 静态资源通常带有文件扩展名（.js/.css/.png/.woff2等）
func isStaticAsset(path string) bool {
	// 已知的静态资源扩展名
	staticExtensions := []string{
		".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg",
		".ico", ".woff", ".woff2", ".ttf", ".eot",
		".json", ".xml", ".txt", ".map",
	}
	lowerPath := strings.ToLower(path)
	for _, ext := range staticExtensions {
		if strings.HasSuffix(lowerPath, ext) {
			return true
		}
	}
	return false
}
