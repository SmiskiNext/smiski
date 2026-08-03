package config

import (
	"testing"
	"time"
)

func clearEnv(t *testing.T) {
	t.Helper()

	for _, key := range []string{"REDIS_ADDR", "REDIS_PASSWORD", "JIRA_API_BASE", "CACHE_TTL", "GRPC_PORT"} {
		t.Setenv(key, "")
	}
}

func TestLoad_Defaults(t *testing.T) {
	clearEnv(t)

	cfg := Load()

	if cfg.RedisAddr != "valkey:6379" {
		t.Errorf("expected default RedisAddr 'valkey:6379', got '%s'", cfg.RedisAddr)
	}

	if cfg.RedisPassword != "" {
		t.Errorf("expected empty default RedisPassword, got '%s'", cfg.RedisPassword)
	}

	if cfg.JiraAPIBase != "https://api.atlassian.com" {
		t.Errorf("expected default JiraAPIBase 'https://api.atlassian.com', got '%s'", cfg.JiraAPIBase)
	}

	if cfg.CacheTTL != 15*time.Minute {
		t.Errorf("expected default CacheTTL 15m, got %s", cfg.CacheTTL)
	}

	if cfg.GRPCPort != "9001" {
		t.Errorf("expected default GRPCPort '9001', got '%s'", cfg.GRPCPort)
	}
}

func TestLoad_EnvironmentOverrides(t *testing.T) {
	t.Setenv("REDIS_ADDR", "cache.internal:6380")
	t.Setenv("REDIS_PASSWORD", "s3cret")
	t.Setenv("JIRA_API_BASE", "https://jira.example.com")
	t.Setenv("CACHE_TTL", "45s")
	t.Setenv("GRPC_PORT", "50051")

	cfg := Load()

	if cfg.RedisAddr != "cache.internal:6380" {
		t.Errorf("expected RedisAddr 'cache.internal:6380', got '%s'", cfg.RedisAddr)
	}

	if cfg.RedisPassword != "s3cret" {
		t.Errorf("expected RedisPassword 's3cret', got '%s'", cfg.RedisPassword)
	}

	if cfg.JiraAPIBase != "https://jira.example.com" {
		t.Errorf("expected JiraAPIBase 'https://jira.example.com', got '%s'", cfg.JiraAPIBase)
	}

	if cfg.CacheTTL != 45*time.Second {
		t.Errorf("expected CacheTTL 45s, got %s", cfg.CacheTTL)
	}

	if cfg.GRPCPort != "50051" {
		t.Errorf("expected GRPCPort '50051', got '%s'", cfg.GRPCPort)
	}
}

func TestLoad_UnparseableCacheTTLFallsBackToDefault(t *testing.T) {
	clearEnv(t)
	t.Setenv("CACHE_TTL", "not-a-duration")

	cfg := Load()

	if cfg.CacheTTL != 15*time.Minute {
		t.Errorf("expected fallback CacheTTL 15m for unparseable value, got %s", cfg.CacheTTL)
	}
}

func TestGetEnv_ReturnsDefaultWhenUnset(t *testing.T) {
	t.Setenv("SMISKI_TEST_UNSET", "")

	if value := getEnv("SMISKI_TEST_UNSET", "fallback"); value != "fallback" {
		t.Errorf("expected 'fallback' for unset key, got '%s'", value)
	}
}

func TestGetEnv_ReturnsValueWhenSet(t *testing.T) {
	t.Setenv("SMISKI_TEST_SET", "configured")

	if value := getEnv("SMISKI_TEST_SET", "fallback"); value != "configured" {
		t.Errorf("expected 'configured' for set key, got '%s'", value)
	}
}
