package config

import (
	"os"
	"strconv"
	"time"
)

const (
	defaultCacheTTL       = 15 * time.Minute
	defaultStaleRetention = 24 * time.Hour
)

// Config holds the gateway runtime settings resolved from the environment.
type Config struct {
	RedisAddr      string
	RedisUsername  string
	RedisPassword  string
	RedisTLS       bool
	JiraAPIBase    string
	CacheTTL       time.Duration
	StaleRetention time.Duration
	GRPCPort       string
}

// Load reads the gateway configuration from environment variables, falling back
// to defaults suited to the local Valkey container. RedisTLS and the RBAC
// credentials allow the same binary to target a managed endpoint such as AWS
// ElastiCache, where in-transit encryption is mandatory.
func Load() *Config {
	return &Config{
		RedisAddr:      getEnv("REDIS_ADDR", "valkey:6379"),
		RedisUsername:  getEnv("REDIS_USERNAME", ""),
		RedisPassword:  getEnv("REDIS_PASSWORD", ""),
		RedisTLS:       getBoolEnv("REDIS_TLS", false),
		JiraAPIBase:    getEnv("JIRA_API_BASE", "https://api.atlassian.com"),
		CacheTTL:       getDurationEnv("CACHE_TTL", defaultCacheTTL),
		StaleRetention: getDurationEnv("CACHE_STALE_RETENTION", defaultStaleRetention),
		GRPCPort:       getEnv("GRPC_PORT", "9001"),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getDurationEnv(key string, defaultValue time.Duration) time.Duration {
	if value := os.Getenv(key); value != "" {
		if parsed, err := time.ParseDuration(value); err == nil {
			return parsed
		}
	}
	return defaultValue
}

func getBoolEnv(key string, defaultValue bool) bool {
	if value := os.Getenv(key); value != "" {
		if parsed, err := strconv.ParseBool(value); err == nil {
			return parsed
		}
	}
	return defaultValue
}
