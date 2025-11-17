package routes

import (
	"fmt"
	"os"
	"runtime"
	"time"

	cpu "github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/host"
	"github.com/shirou/gopsutil/v4/process"
)

// 服务器配置
type ServerConfig struct {
	Host         string
	Port         string
	ReadTimeout  time.Duration
	WriteTimeout time.Duration
	IdleTimeout  time.Duration
}

// 健康检查API响应结构体 - 确保字段顺序
type HealthCheckResponse struct {
	Status     string         `json:"status"`
	Timestamp  string         `json:"timestamp"`
	Version    string         `json:"version"`
	Uptime     string         `json:"uptime"`
	Environment SystemInfo    `json:"environment"`
	Resources  ResourceInfo   `json:"resources"`
	Services   ServiceInfo    `json:"services"`
	Paths      PathInfo       `json:"paths"`
}

// 轻量级健康检查API响应结构体 - 确保字段顺序
type HealthCheckLiteResponse struct {
	Status     string `json:"status"`
	Timestamp  string `json:"timestamp"`
	Database   string `json:"database"`
}

type SystemInfo struct {
	GoVersion     string `json:"goVersion"`
	GoEnv         string `json:"goEnv"`
	Platform      string `json:"platform"`
	Arch          string `json:"arch"`
	Hostname      string `json:"hostname"`
	OS            string `json:"os"`
	Kernel        string `json:"kernel"`
	SystemUptime  string `json:"systemUptime"`
}

type ResourceInfo struct {
	Memory MemoryInfo `json:"memory"`
	CPU    CPUInfo    `json:"cpu"`
}

type MemoryInfo struct {
	RSS        string `json:"rss"`
	HeapTotal  string `json:"heapTotal"`
	HeapUsed   string `json:"heapUsed"`
	External   string `json:"external"`
}

type CPUInfo struct {
	Count        int    `json:"count"`
	Model        string `json:"model"`
	UsagePercent string `json:"usagePercent"`
	SystemLoad   struct {
		Message   string   `json:"message"`
		RawValue  [3]string `json:"rawValue"`
	} `json:"systemLoad"`
}

type ServiceInfo struct {
	Database   DatabaseInfo   `json:"database"`
	FileSystem FileSystemInfo `json:"fileSystem"`
}

type DatabaseInfo struct {
	Status string `json:"status"`
	Error  string `json:"error"`
}

type FileSystemInfo struct {
	ServerDirExists  bool `json:"serverDirExists"`
	ClientDistExists bool `json:"clientDistExists"`
	ConfigExists     bool `json:"configExists"`
}

type PathInfo struct {
	ServerDir        string `json:"serverDir"`
	ClientDistPath   string `json:"clientDistPath"`
	ServerConfigPath string `json:"serverConfigPath"`
}

// 获取CPU使用率和信息
func getCPUInfo() (float64, []cpu.InfoStat, error) {
	// 获取CPU使用率
	cpuPercent, err := cpu.Percent(0, false)
	if err != nil {
		return 0, nil, fmt.Errorf("获取CPU使用率失败: %v", err)
	}
	
	usage := 0.0
	if len(cpuPercent) > 0 {
		usage = cpuPercent[0]
	}
	
	// 获取CPU详细信息
	cpuInfo, err := cpu.Info()
	if err != nil {
		return usage, nil, fmt.Errorf("获取CPU信息失败: %v", err)
	}
	
	return usage, cpuInfo, nil
}

// 获取当前进程CPU使用率
func getProcessCPUUsage() (float64, error) {
	p, err := process.NewProcess(int32(os.Getpid()))
	if err != nil {
		return 0, err
	}
	
	return p.Percent(0)
}

// 获取系统详细信息
func getSystemInfo() (*SystemInfo, error) {
	info := &SystemInfo{
		GoVersion: runtime.Version(),
		GoEnv:     runtime.GOOS + "/" + runtime.GOARCH,
		Arch:      runtime.GOARCH,
	}
	
	// 使用gopsutil获取主机信息
	hostInfo, err := host.Info()
	if err != nil {
		return info, fmt.Errorf("获取主机信息失败: %v", err)
	}
	
	info.Hostname = hostInfo.Hostname
	info.Platform = hostInfo.Platform
	info.OS = hostInfo.OS
	info.Kernel = hostInfo.KernelVersion
	
	// 计算系统运行时间
	if hostInfo.BootTime > 0 {
		uptime := time.Since(time.Unix(int64(hostInfo.BootTime), 0))
		info.SystemUptime = uptime.String()
	}
	
	return info, nil
}

// 获取内存使用情况（当前进程）
func getMemoryInfo() (*MemoryInfo, error) {
	p, err := process.NewProcess(int32(os.Getpid()))
	if err != nil {
		return nil, fmt.Errorf("获取进程信息失败: %v", err)
	}
	
	// 获取进程内存使用情况
	memInfo, err := p.MemoryInfo()
	if err != nil {
		return nil, fmt.Errorf("获取进程内存信息失败: %v", err)
	}
	
	// 获取进程内存使用百分比
	memPercent, err := p.MemoryPercent()
	if err != nil {
		return nil, fmt.Errorf("获取进程内存使用百分比失败: %v", err)
	}
	
	return &MemoryInfo{
		RSS:       formatBytes(memInfo.RSS),
		HeapTotal: formatBytes(memInfo.VMS),
		HeapUsed:  fmt.Sprintf("%.1f%%", float64(memPercent)),
		External:  formatBytes(memInfo.Data),
	}, nil
}

// 格式化字节大小
func formatBytes(bytes uint64) string {
	const (
		KB = 1024
		MB = KB * 1024
		GB = MB * 1024
		TB = GB * 1024
	)
	
	switch {
	case bytes >= TB:
		return fmt.Sprintf("%.2f TB", float64(bytes)/TB)
	case bytes >= GB:
		return fmt.Sprintf("%.2f GB", float64(bytes)/GB)
	case bytes >= MB:
		return fmt.Sprintf("%.2f MB", float64(bytes)/MB)
	case bytes >= KB:
		return fmt.Sprintf("%.2f KB", float64(bytes)/KB)
	default:
		return fmt.Sprintf("%d B", bytes)
	}
}

// 默认服务器配置
func GetDefaultConfig() *ServerConfig {
	return &ServerConfig{
		Host:         "0.0.0.0",
		Port:         "8080",
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  120 * time.Second,
	}
}