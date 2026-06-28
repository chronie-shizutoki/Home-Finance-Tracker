package routes

import (
	"github.com/gin-gonic/gin"
	"homemoney/internal/handler"
	"homemoney/internal/service"
)

// SetupJsonFileRoutes sets up JSON file operation related routes
func SetupJsonFileRoutes(router *gin.RouterGroup, jsonFileService *service.JsonFileService) {
	// Create handler instance
	jsonFileHandler := handler.NewJsonFileHandler(jsonFileService)

	// Define JSON file operation route group
	jsonFileRoutes := router.Group("/json-files")
	{
		// GET /api/json-files/:filename - Read specified JSON file
		jsonFileRoutes.GET("/:filename", jsonFileHandler.ReadJsonFile)

		// POST /api/json-files/:filename - Write data to specified JSON file
		jsonFileRoutes.POST("/:filename", jsonFileHandler.WriteJsonFile)

		// GET /api/json-files - Get all JSON file list
		jsonFileRoutes.GET("", jsonFileHandler.GetJsonFileList)

		// DELETE /api/json-files/:filename - Delete specified JSON file
		jsonFileRoutes.DELETE("/:filename", jsonFileHandler.DeleteJsonFile)
	}
}