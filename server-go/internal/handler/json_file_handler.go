package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"homemoney/internal/service"
)

// JsonFileHandler JSON file operation handler
type JsonFileHandler struct {
	jsonFileService *service.JsonFileService
}

// NewJsonFileHandler creates a new JSON file handler
func NewJsonFileHandler(jsonFileService *service.JsonFileService) *JsonFileHandler {
	return &JsonFileHandler{
		jsonFileService: jsonFileService,
	}
}

// ReadJsonFile reads a specified JSON file
// @Summary Read JSON file
// @Description Read the content of a specified JSON file
// @Tags JSON file operations
// @Accept json
// @Produce json
// @Param filename path string true "Filename"
// @Success 200 {object} map[string]interface{} "{"success":true,"data":{...}}"
// @Failure 400 {object} map[string]interface{} "{"success":false,"error":"Error message"}"
// @Failure 500 {object} map[string]interface{} "{"success":false,"error":"Error message"}"
// @Router /api/json-files/{filename} [get]
func (h *JsonFileHandler) ReadJsonFile(c *gin.Context) {
	// Get filename parameter
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "Filename cannot be empty",
		})
		return
	}

	// Call service layer to read file
	data, err := h.jsonFileService.ReadJsonFile(filename)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	// Return success response
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    data,
	})
}

// WriteJsonFile writes data to a specified JSON file
// @Summary Write JSON file
// @Description Write JSON data from request body to the specified file
// @Tags JSON file operations
// @Accept json
// @Produce json
// @Param filename path string true "Filename"
// @Param fileData body map[string]interface{} true "JSON data to write"
// @Success 200 {object} map[string]interface{} "{"success":true,"message":"File written successfully","filePath":"File path"}"
// @Failure 400 {object} map[string]interface{} "{"success":false,"error":"Error message"}"
// @Failure 500 {object} map[string]interface{} "{"success":false,"error":"Error message"}"
// @Router /api/json-files/{filename} [post]
func (h *JsonFileHandler) WriteJsonFile(c *gin.Context) {
	// Get filename parameter
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "Filename cannot be empty",
		})
		return
	}

	// Parse request body
	var fileData map[string]interface{}
	if err := c.ShouldBindJSON(&fileData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "Invalid JSON data: " + err.Error(),
		})
		return
	}

	// Call service layer to write file
	filePath, err := h.jsonFileService.WriteJsonFile(filename, fileData)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	// Return success response
	c.JSON(http.StatusOK, gin.H{
		"success":  true,
		"message":  "File written successfully",
		"filePath": filePath,
	})
}

// GetJsonFileList gets all JSON file list
// @Summary Get JSON file list
// @Description Get all available JSON file list
// @Tags JSON file operations
// @Accept json
// @Produce json
// @Success 200 {object} map[string]interface{} "{"success":true,"files":["file1","file2"]}"
// @Failure 500 {object} map[string]interface{} "{"success":false,"error":"Error message"}"
// @Router /api/json-files [get]
func (h *JsonFileHandler) GetJsonFileList(c *gin.Context) {
	// Call service layer to get file list
	files, err := h.jsonFileService.GetJsonFileList()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	// Return success response
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"files":   files,
	})
}

// DeleteJsonFile deletes a specified JSON file
// @Summary Delete JSON file
// @Description Delete a specified JSON file
// @Tags JSON file operations
// @Accept json
// @Produce json
// @Param filename path string true "Filename"
// @Success 200 {object} map[string]interface{} "{"success":true,"message":"File deleted successfully"}"
// @Failure 400 {object} map[string]interface{} "{"success":false,"error":"Error message"}"
// @Failure 404 {object} map[string]interface{} "{"success":false,"error":"File not found"}"
// @Failure 500 {object} map[string]interface{} "{"success":false,"error":"Error message"}"
// @Router /api/json-files/{filename} [delete]
func (h *JsonFileHandler) DeleteJsonFile(c *gin.Context) {
	// Get filename parameter
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "Filename cannot be empty",
		})
		return
	}

	// Call service layer to delete file
	err := h.jsonFileService.DeleteJsonFile(filename)
	if err != nil {
		// Determine error type
		if err.Error() == "file not found" {
			c.JSON(http.StatusNotFound, gin.H{
				"success": false,
				"error":   err.Error(),
			})
		} else {
			c.JSON(http.StatusInternalServerError, gin.H{
				"success": false,
				"error":   err.Error(),
			})
		}
		return
	}

	// Return success response
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "File deleted successfully",
	})
}