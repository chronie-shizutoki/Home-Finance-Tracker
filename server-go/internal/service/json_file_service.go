package service

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// JsonFileService JSON file operation service
type JsonFileService struct {
	// JSON file storage directory
	storageDir string
}

// NewJsonFileService creates a new JSON file service instance
func NewJsonFileService() *JsonFileService {
	// Default storage directory
	storageDir := filepath.Join("./data/json-files")
	return &JsonFileService{
		storageDir: storageDir,
	}
}

// ReadJsonFile reads a JSON file
func (s *JsonFileService) ReadJsonFile(filename string) (map[string]interface{}, error) {
	// Ensure filename format is correct
	filename = sanitizeFilename(filename)
	filePath := filepath.Join(s.storageDir, filename+".json")

	// Read file content
	data, err := os.ReadFile(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			// File does not exist, return empty object
			return make(map[string]interface{}), nil
		}
		return nil, fmt.Errorf("failed to read file: %w", err)
	}

	// Parse JSON
	var result map[string]interface{}
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("failed to parse JSON: %w", err)
	}

	return result, nil
}

// WriteJsonFile writes a JSON file
func (s *JsonFileService) WriteJsonFile(filename string, data map[string]interface{}) (string, error) {
	// Ensure filename format is correct
	filename = sanitizeFilename(filename)
	filePath := filepath.Join(s.storageDir, filename+".json")

	// Ensure directory exists
	if err := os.MkdirAll(s.storageDir, 0755); err != nil {
		return "", fmt.Errorf("failed to create directory: %w", err)
	}

	// Format JSON and write to file
	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return "", fmt.Errorf("failed to serialize JSON: %w", err)
	}

	if err := os.WriteFile(filePath, jsonData, 0644); err != nil {
		return "", fmt.Errorf("failed to write file: %w", err)
	}

	return filePath, nil
}

// GetJsonFileList gets the JSON file list
func (s *JsonFileService) GetJsonFileList() ([]string, error) {
	// Ensure directory exists
	if err := os.MkdirAll(s.storageDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create directory: %w", err)
	}

	// Read directory contents
	entries, err := os.ReadDir(s.storageDir)
	if err != nil {
		return nil, fmt.Errorf("failed to read directory: %w", err)
	}

	// Filter JSON files and remove .json suffix
	var jsonFiles []string
	for _, entry := range entries {
		if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".json") {
			// Remove .json suffix
			fileName := strings.TrimSuffix(entry.Name(), ".json")
			jsonFiles = append(jsonFiles, fileName)
		}
	}

	return jsonFiles, nil
}

// DeleteJsonFile deletes a JSON file
func (s *JsonFileService) DeleteJsonFile(filename string) error {
	// Ensure filename format is correct
	filename = sanitizeFilename(filename)
	filePath := filepath.Join(s.storageDir, filename+".json")

	// Check if file exists
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		return fmt.Errorf("file not found")
	}

	// Delete file
	if err := os.Remove(filePath); err != nil {
		return fmt.Errorf("failed to delete file: %w", err)
	}

	return nil
}

// sanitizeFilename cleans filename to prevent path traversal attacks
func sanitizeFilename(filename string) string {
	// Remove path separators and other unsafe characters
	sanitized := strings.ReplaceAll(filename, "/", "_")
	sanitized = strings.ReplaceAll(sanitized, "\\", "_")
	sanitized = strings.ReplaceAll(sanitized, "..", "_")
	return sanitized
}