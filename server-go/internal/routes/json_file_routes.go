package routes

import (
	"github.com/gin-gonic/gin"
	"homemoney/internal/handler"
	"homemoney/internal/service"
)

// SetupJsonFileRoutes 设置JSON文件操作相关路由
func SetupJsonFileRoutes(router *gin.RouterGroup, jsonFileService *service.JsonFileService) {
	// 创建处理器实例
	jsonFileHandler := handler.NewJsonFileHandler(jsonFileService)
	
	// 定义JSON文件操作的路由组
	jsonFileRoutes := router.Group("/json-files")
	{
		// GET /api/json-files/:filename - 读取指定JSON文件
		jsonFileRoutes.GET("/:filename", jsonFileHandler.ReadJsonFile)
		
		// POST /api/json-files/:filename - 写入数据到指定JSON文件
		jsonFileRoutes.POST("/:filename", jsonFileHandler.WriteJsonFile)
		
		// GET /api/json-files - 获取所有JSON文件列表
		jsonFileRoutes.GET("", jsonFileHandler.GetJsonFileList)
		
		// DELETE /api/json-files/:filename - 删除指定JSON文件
		jsonFileRoutes.DELETE("/:filename", jsonFileHandler.DeleteJsonFile)
		
		// GET /api/json-files/:filename/info - 获取文件信息
		jsonFileRoutes.GET("/:filename/info", jsonFileHandler.GetFileInfo)
	}
}