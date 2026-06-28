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
	// Record server start time
	startTime := time.Now()

	// Initialize logging
	log.SetFlags(log.LstdFlags | log.Lshortfile)

	// Load configuration
	config := routes.GetDefaultConfig()
	if port := os.Getenv("PORT"); port != "" {
		config.Port = port
	}

	// Initialize database
	db, err := database.InitDB("./database.sqlite")
	if err != nil {
		log.Fatalf("Database initialization failed: %v", err)
	}
	defer func() { _ = db.Close() }()

	// Create Repository instances
	expenseRepo := repository.NewExpenseRepository(db.GetDB())

	// Create member-related Repository instance
	memberRepo := repository.NewMemberRepository(db.GetDB())

	// Set Gin mode
	if os.Getenv("GIN_MODE") == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	// Create router engine
	router := gin.New()

	// Add middleware
	router.Use(
		// Recover from panic
		gin.CustomRecovery(func(c *gin.Context, recovered interface{}) {
			log.Printf("Panic recovered: %v", recovered)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "Internal server error",
				"code":    "INTERNAL_ERROR",
			})
		}),
		// Request logging
		gin.Logger(),
		// CORS middleware - consistent with JS version
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

	// Increase JSON parsing size limit, consistent with JS version (10MB)
	router.MaxMultipartMemory = 10 << 20 // 10MB

	// Set up system-related routes (health check and API docs)
	sqlDB, err := db.GetDB().DB()
	if err != nil {
		log.Fatalf("Failed to get underlying SQL DB: %v", err)
	}
	routes.SetupHealthRoutes(router, startTime, sqlDB)
	routes.SetupHelpRoutes(router)

	// Set up API routes
	routes.SetupExpenseRoutes(router, expenseRepo)

	// Set up member-related API routes - corresponding to JS version memberRoutes
	routes.SetupMemberRoutes(router, memberRepo)

	// Set up error report routes
	errorReportRepo := repository.NewErrorReportRepository(db.GetDB())
	routes.SetupErrorReportRoutes(router, errorReportRepo)

	// Set up export/import routes
	routes.SetupExportRoutes(router, expenseRepo)

	// Initialize service instances
	// Create JSON file service instance
	jsonFileService := service.NewJsonFileService()
	// Create log service instance
	logService := service.NewLogService(db.GetDB())

	// Register routes
	routes.SetupJsonFileRoutes(router.Group("/api"), jsonFileService)
	routes.SetupLogRoutes(router.Group("/api"), logService)

	// Serve frontend static files (auto-enabled when client/dist exists)
	distPath := filepath.Join(".", "client", "dist")
	if _, err := os.Stat(filepath.Join(distPath, "index.html")); err == nil {
		log.Printf("Serving static files from: %s", distPath)
		router.Static("/assets", filepath.Join(distPath, "assets"))
		router.StaticFile("/favicon.ico", filepath.Join(distPath, "favicon.ico"))
		router.StaticFile("/photo.html", filepath.Join(distPath, "photo.html"))
		router.GET("/", func(c *gin.Context) {
			c.File(filepath.Join(distPath, "index.html"))
		})
		// SPA fallback - return index.html for page routes only, 404 for static asset requests
		// Avoid returning index.html for JS/CSS static asset requests which causes MIME type errors
		router.NoRoute(func(c *gin.Context) {
			path := c.Request.URL.Path
			// API paths return 404 (API routes should already be registered)
			// Static asset requests with extensions (.js/.css/.png etc.) return 404 instead of index.html
			if strings.HasPrefix(path, "/api/") || isStaticAsset(path) {
				c.Status(http.StatusNotFound)
				return
			}
			// Page routes return index.html for SPA
			c.File(filepath.Join(distPath, "index.html"))
		})
	}

	// Create HTTP server
	srv := &http.Server{
		Addr:         config.Host + ":" + config.Port,
		Handler:      router,
		ReadTimeout:  config.ReadTimeout,
		WriteTimeout: config.WriteTimeout,
		IdleTimeout:  config.IdleTimeout,
	}

	// Start server
	go func() {
		log.Printf("Server started on port %s", config.Port)
		log.Printf("API docs: http://localhost:%s/api", config.Port)

		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server failed to start: %v", err)
		}
	}()

	// Wait for interrupt signal to gracefully shutdown server
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server...")

	// Gracefully shutdown server
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced shutdown: %v", err)
	}

	log.Println("Server exited")
}

// SetupLogLevel sets the log level
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

// isStaticAsset checks if the request path is a static asset request
// Static assets typically have file extensions (.js/.css/.png/.woff2 etc.)
func isStaticAsset(path string) bool {
	// Known static asset extensions
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
