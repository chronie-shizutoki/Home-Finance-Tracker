package routes

import (
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/gin-gonic/gin"
)

// SetupHealthRoutes 设置健康检查相关的路由
func SetupHealthRoutes(router *gin.Engine, startTime time.Time) {
	// 健康检查端点 - 使用gopsutil获取专业监控数据
	router.GET("/api/health", func(c *gin.Context) {
		// 使用gopsutil获取系统信息
		systemInfo, err := getSystemInfo()
		if err != nil {
			log.Printf("获取系统信息失败: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "获取系统信息失败: " + err.Error(),
			})
			return
		}

		// 使用gopsutil获取内存信息
		memoryInfo, err := getMemoryInfo()
		if err != nil {
			log.Printf("获取内存信息失败: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "获取内存信息失败: " + err.Error(),
			})
			return
		}

		// 使用gopsutil获取当前进程CPU使用率
		processCPUUsage, err := getProcessCPUUsage()
		if err != nil {
			log.Printf("获取进程CPU使用率失败: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "获取进程CPU使用率失败: " + err.Error(),
			})
			return
		}

		// 使用gopsutil获取CPU信息
		_, cpuInfo, err := getCPUInfo()
		if err != nil {
			log.Printf("获取CPU信息失败: %v", err)
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   "获取CPU信息失败: " + err.Error(),
			})
			return
		}

		// 检查数据库连接
		dbStatus := "connected"
		dbError := ""
		sqlDB := getDB()
		if sqlDB == nil {
			dbStatus = "disconnected"
			dbError = "数据库实例未提供"
		} else if err := sqlDB.Ping(); err != nil {
			dbStatus = "disconnected"
			dbError = err.Error()
		}

		// 检查文件系统路径
		serverDir := "D:\\chronie-app\\homemoney\\server\\src"
		clientDistPath := "D:\\chronie-app\\homemoney\\client\\dist"
		serverConfigPath := "D:\\chronie-app\\homemoney\\server\\config\\config.js"

		// 检查文件/目录是否存在
		serverDirExists := true
		clientDistExists := true
		configExists := true

		// 使用Go的os包检查路径
		if _, err := os.Stat(serverDir); os.IsNotExist(err) {
			serverDirExists = false
		}
		if _, err := os.Stat(clientDistPath); os.IsNotExist(err) {
			clientDistExists = false
		}
		if _, err := os.Stat(serverConfigPath); os.IsNotExist(err) {
			configExists = false
		}

		// 构建CPU信息 - 使用gopsutil获取的数据
		var cpuModel string
		var cpuCount int
		if len(cpuInfo) > 0 {
			cpuModel = cpuInfo[0].ModelName
			cpuCount = int(cpuInfo[0].Cores)
		} else {
			cpuModel = "Unknown CPU"
			cpuCount = 0
		}

		cpuInfoStruct := CPUInfo{
			Count:        cpuCount,
			Model:        cpuModel,
			UsagePercent: fmt.Sprintf("%.1f%%", processCPUUsage),
		}
		cpuInfoStruct.SystemLoad.Message = "Windows does not support load average as unix"
		cpuInfoStruct.SystemLoad.RawValue = [3]string{"0.00", "0.00", "0.00"}

		// 构建资源信息
		resourceInfo := ResourceInfo{
			Memory: *memoryInfo,
			CPU:    cpuInfoStruct,
		}

		// 构建数据库信息
		databaseInfo := DatabaseInfo{
			Status: dbStatus,
			Error:  dbError,
		}

		// 构建文件系统信息
		fileSystemInfo := FileSystemInfo{
			ServerDirExists:  serverDirExists,
			ClientDistExists: clientDistExists,
			ConfigExists:     configExists,
		}

		// 构建服务信息
		serviceInfo := ServiceInfo{
			Database:   databaseInfo,
			FileSystem: fileSystemInfo,
		}

		// 构建路径信息
		pathInfo := PathInfo{
			ServerDir:        serverDir,
			ClientDistPath:   clientDistPath,
			ServerConfigPath: serverConfigPath,
		}

		// 构建健康状态数据 - 使用结构体确保顺序
		healthData := HealthCheckResponse{
			Status:      "OK",
			Timestamp:   time.Now().UTC().Format(time.RFC3339),
			Version:     "3.0.0",
			Uptime:      fmt.Sprintf("%.2fs", time.Since(startTime).Seconds()),
			Environment: *systemInfo,
			Resources:   resourceInfo,
			Services:    serviceInfo,
			Paths:       pathInfo,
		}

		c.JSON(http.StatusOK, healthData)
	})

	// 轻量级健康检查端点 - 与Node.js版本完全一致
	router.GET("/api/health/lite", func(c *gin.Context) {
		// 检查数据库连接
		dbStatus := "connected"
		sqlDB := getDB()
		if sqlDB == nil {
			dbStatus = "disconnected"
		} else if err := sqlDB.Ping(); err != nil {
			dbStatus = "disconnected"
		}

		// 轻量级健康状态数据 - 与Node.js版本完全一致
		healthData := HealthCheckLiteResponse{
			Status:     "OK",
			Timestamp:  time.Now().UTC().Format(time.RFC3339),
			Database:   dbStatus,
		}

		c.JSON(http.StatusOK, healthData)
	})
}

// 辅助函数：获取数据库实例 - 这个函数需要在调用者中提供
func getDB() *sql.DB {
	// 这个函数需要从外部注入数据库实例
	// 暂时返回nil，实际使用时需要通过参数传递
	log.Println("Warning: getDB() called but no database instance provided")
	return nil
}