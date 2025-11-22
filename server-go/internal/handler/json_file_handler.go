package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"homemoney/internal/service"
)

// JsonFileHandler JSON文件操作处理器
type JsonFileHandler struct {
	jsonFileService *service.JsonFileService
}

// NewJsonFileHandler 创建新的JSON文件处理器
func NewJsonFileHandler(jsonFileService *service.JsonFileService) *JsonFileHandler {
	return &JsonFileHandler{
		jsonFileService: jsonFileService,
	}
}

// ReadJsonFile 读取指定JSON文件
// @Summary 读取JSON文件
// @Description 读取指定名称的JSON文件内容
// @Tags JSON文件操作
// @Accept json
// @Produce json
// @Param filename path string true "文件名"
// @Success 200 {object} map[string]interface{} "{\"success\":true,\"data\":{...}}"
// @Failure 400 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Failure 500 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Router /api/json-files/{filename} [get]
func (h *JsonFileHandler) ReadJsonFile(c *gin.Context) {
	// 获取文件名参数
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "文件名不能为空",
		})
		return
	}

	// 调用服务层读取文件
	data, err := h.jsonFileService.ReadJsonFile(filename)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	// 返回成功响应
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data":    data,
	})
}

// WriteJsonFile 写入数据到指定JSON文件
// @Summary 写入JSON文件
// @Description 将请求体中的JSON数据写入指定名称的文件
// @Tags JSON文件操作
// @Accept json
// @Produce json
// @Param filename path string true "文件名"
// @Param fileData body map[string]interface{} true "要写入的JSON数据"
// @Success 200 {object} map[string]interface{} "{\"success\":true,\"message\":\"文件写入成功\",\"filePath\":\"文件路径\"}"
// @Failure 400 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Failure 500 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Router /api/json-files/{filename} [post]
func (h *JsonFileHandler) WriteJsonFile(c *gin.Context) {
	// 获取文件名参数
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "文件名不能为空",
		})
		return
	}

	// 解析请求体
	var fileData map[string]interface{}
	if err := c.ShouldBindJSON(&fileData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "无效的JSON数据: " + err.Error(),
		})
		return
	}

	// 调用服务层写入文件
	filePath, err := h.jsonFileService.WriteJsonFile(filename, fileData)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	// 返回成功响应
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "文件写入成功",
		"filePath": filePath,
	})
}

// GetJsonFileList 获取所有JSON文件列表
// @Summary 获取JSON文件列表
// @Description 获取所有可用的JSON文件列表
// @Tags JSON文件操作
// @Accept json
// @Produce json
// @Success 200 {object} map[string]interface{} "{\"success\":true,\"files\":[\"文件1\",\"文件2\"]}"
// @Failure 500 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Router /api/json-files [get]
func (h *JsonFileHandler) GetJsonFileList(c *gin.Context) {
	// 调用服务层获取文件列表
	files, err := h.jsonFileService.GetJsonFileList()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	// 返回成功响应
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"files":   files,
	})
}

// DeleteJsonFile 删除指定JSON文件
// @Summary 删除JSON文件
// @Description 删除指定名称的JSON文件
// @Tags JSON文件操作
// @Accept json
// @Produce json
// @Param filename path string true "文件名"
// @Success 200 {object} map[string]interface{} "{\"success\":true,\"message\":\"文件删除成功\"}"
// @Failure 400 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Failure 404 {object} map[string]interface{} "{\"success\":false,\"error\":\"文件不存在\"}"
// @Failure 500 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Router /api/json-files/{filename} [delete]
func (h *JsonFileHandler) DeleteJsonFile(c *gin.Context) {
	// 获取文件名参数
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "文件名不能为空",
		})
		return
	}

	// 调用服务层删除文件
	err := h.jsonFileService.DeleteJsonFile(filename)
	if err != nil {
		// 判断错误类型
		if err.Error() == "文件不存在" {
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

	// 返回成功响应
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"message": "文件删除成功",
	})
}

// GetFileInfo 获取文件信息
// @Summary 获取文件信息
// @Description 获取指定JSON文件的详细信息
// @Tags JSON文件操作
// @Accept json
// @Produce json
// @Param filename path string true "文件名"
// @Success 200 {object} map[string]interface{} "{\"success\":true,\"info\":{...}}"
// @Failure 400 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Failure 404 {object} map[string]interface{} "{\"success\":false,\"error\":\"文件不存在\"}"
// @Failure 500 {object} map[string]interface{} "{\"success\":false,\"error\":\"错误信息\"}"
// @Router /api/json-files/{filename}/info [get]
func (h *JsonFileHandler) GetFileInfo(c *gin.Context) {
	// 获取文件名参数
	filename := c.Param("filename")
	if filename == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   "文件名不能为空",
		})
		return
	}

	// 调用服务层获取文件信息
	info, err := h.jsonFileService.GetFileInfo(filename)
	if err != nil {
		// 判断错误类型
		if err.Error() == "文件不存在" {
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

	// 返回成功响应
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"info":    info,
	})
}