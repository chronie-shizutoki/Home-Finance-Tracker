package service

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// JsonFileService JSON文件操作服务
type JsonFileService struct {
	// JSON文件存储目录
	storageDir string
}

// NewJsonFileService 创建新的JSON文件服务实例
func NewJsonFileService() *JsonFileService {
	// 默认存储目录
	storageDir := filepath.Join("./data/json-files")
	return &JsonFileService{
		storageDir: storageDir,
	}
}

// ReadJsonFile 读取JSON文件
func (s *JsonFileService) ReadJsonFile(filename string) (map[string]interface{}, error) {
	// 确保文件名格式正确
	filename = sanitizeFilename(filename)
	filePath := filepath.Join(s.storageDir, filename+".json")
	
	// 读取文件内容
	data, err := os.ReadFile(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			// 文件不存在，返回空对象
			return make(map[string]interface{}), nil
		}
		return nil, fmt.Errorf("读取文件失败: %w", err)
	}
	
	// 解析JSON
	var result map[string]interface{}
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("解析JSON失败: %w", err)
	}
	
	return result, nil
}

// WriteJsonFile 写入JSON文件
func (s *JsonFileService) WriteJsonFile(filename string, data map[string]interface{}) (string, error) {
	// 确保文件名格式正确
	filename = sanitizeFilename(filename)
	filePath := filepath.Join(s.storageDir, filename+".json")
	
	// 确保目录存在
	if err := os.MkdirAll(s.storageDir, 0755); err != nil {
		return "", fmt.Errorf("创建目录失败: %w", err)
	}
	
	// 格式化JSON并写入文件
	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return "", fmt.Errorf("JSON序列化失败: %w", err)
	}
	
	if err := os.WriteFile(filePath, jsonData, 0644); err != nil {
		return "", fmt.Errorf("写入文件失败: %w", err)
	}
	
	return filePath, nil
}

// GetJsonFileList 获取JSON文件列表
func (s *JsonFileService) GetJsonFileList() ([]string, error) {
	// 确保目录存在
	if err := os.MkdirAll(s.storageDir, 0755); err != nil {
		return nil, fmt.Errorf("创建目录失败: %w", err)
	}
	
	// 读取目录内容
	entries, err := os.ReadDir(s.storageDir)
	if err != nil {
		return nil, fmt.Errorf("读取目录失败: %w", err)
	}
	
	// 过滤出JSON文件并移除.json后缀
	var jsonFiles []string
	for _, entry := range entries {
		if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".json") {
			// 移除.json后缀
			fileName := strings.TrimSuffix(entry.Name(), ".json")
			jsonFiles = append(jsonFiles, fileName)
		}
	}
	
	return jsonFiles, nil
}

// DeleteJsonFile 删除JSON文件
func (s *JsonFileService) DeleteJsonFile(filename string) error {
	// 确保文件名格式正确
	filename = sanitizeFilename(filename)
	filePath := filepath.Join(s.storageDir, filename+".json")
	
	// 检查文件是否存在
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		return fmt.Errorf("文件不存在")
	}
	
	// 删除文件
	if err := os.Remove(filePath); err != nil {
		return fmt.Errorf("删除文件失败: %w", err)
	}
	
	return nil
}

// GetFileInfo 获取文件信息
func (s *JsonFileService) GetFileInfo(filename string) (map[string]interface{}, error) {
	// 确保文件名格式正确
	filename = sanitizeFilename(filename)
	filePath := filepath.Join(s.storageDir, filename+".json")
	
	// 获取文件信息
	fileInfo, err := os.Stat(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("文件不存在")
		}
		return nil, fmt.Errorf("获取文件信息失败: %w", err)
	}
	
	// 构建文件信息响应
	info := map[string]interface{}{
		"filename":    filename,
		"size":        fileInfo.Size(),
		"modified_at": fileInfo.ModTime().Format(time.RFC3339),
		"created_at":  fileInfo.ModTime().Format(time.RFC3339), // 在Go中，我们使用ModTime作为创建时间的近似值
	}
	
	return info, nil
}

// sanitizeFilename 清理文件名，防止路径遍历攻击
func sanitizeFilename(filename string) string {
	// 移除路径分隔符和其他不安全字符
	sanitized := strings.ReplaceAll(filename, "/", "_")
	sanitized = strings.ReplaceAll(sanitized, "\\", "_")
	sanitized = strings.ReplaceAll(sanitized, "..", "_")
	return sanitized
}