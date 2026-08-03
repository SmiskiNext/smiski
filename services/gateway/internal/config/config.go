package config

import (
	"os"
	"time"
)

type Config struct {
	RedisAddr     string
	RedisPassword string
	JiraAPIBase   string
	CacheTTL      time.Duration
	GRPCPort      string
}

func Load() *Config {
	cacheTTL := 15 * time.Minute
	if ttl := os.Getenv("CACHE_TTL"); ttl != "" {
		if d, err := time.ParseDuration(ttl); err == nil {
			cacheTTL = d
		}
	}

	return &Config{
		RedisAddr:     getEnv("REDIS_ADDR", "valkey:6379"),
		RedisPassword: getEnv("REDIS_PASSWORD", ""),
		JiraAPIBase:   getEnv("JIRA_API_BASE", "https://api.atlassian.com"),
		CacheTTL:      cacheTTL,
		GRPCPort:      getEnv("GRPC_PORT", "9001"),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
