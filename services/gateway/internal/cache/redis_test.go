package cache

import (
	"context"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
)

func TestCache_GetExisting(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(mr.Addr(), "", 15*time.Minute)
	defer cache.Close()

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"
	permissions := []string{"view-meeting", "edit-meeting"}

	if err := cache.Set(ctx, key, permissions); err != nil {
		t.Fatalf("failed to set cache: %v", err)
	}

	result, err := cache.Get(ctx, key)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result) != 2 {
		t.Errorf("expected 2 permissions, got %d", len(result))
	}

	if result[0] != "view-meeting" || result[1] != "edit-meeting" {
		t.Errorf("unexpected permissions: %v", result)
	}
}

func TestCache_GetMissing(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(mr.Addr(), "", 15*time.Minute)
	defer cache.Close()

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"

	result, err := cache.Get(ctx, key)
	if err != nil {
		t.Fatalf("expected no error for missing key, got %v", err)
	}

	if result != nil {
		t.Errorf("expected nil for missing key, got %v", result)
	}
}

func TestCache_SetWithTTL(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(mr.Addr(), "", 1*time.Second)
	defer cache.Close()

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"
	permissions := []string{"view-meeting"}

	if err := cache.Set(ctx, key, permissions); err != nil {
		t.Fatalf("failed to set cache: %v", err)
	}

	result, err := cache.Get(ctx, key)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result) != 1 {
		t.Errorf("expected 1 permission, got %d", len(result))
	}

	mr.FastForward(2 * time.Second)

	result, err = cache.Get(ctx, key)
	if err != nil {
		t.Fatalf("expected no error for expired key, got %v", err)
	}

	if result != nil {
		t.Errorf("expected nil for expired key, got %v", result)
	}
}

func TestCache_GetStaleExpired(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(mr.Addr(), "", 1*time.Second)
	defer cache.Close()

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"
	permissions := []string{"view-meeting"}

	if err := cache.Set(ctx, key, permissions); err != nil {
		t.Fatalf("failed to set cache: %v", err)
	}

	mr.FastForward(2 * time.Second)

	result, err := cache.GetStale(ctx, key)
	if err == nil {
		t.Errorf("expected error for expired key in GetStale, got nil with result: %v", result)
	}
}

func TestCache_ConnectionFailureBypass(t *testing.T) {
	cache := NewCache("invalid:6379", "", 15*time.Minute)
	defer cache.Close()

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"

	_, err := cache.Get(ctx, key)
	if err == nil {
		t.Fatal("expected error for connection failure, got nil")
	}
}

func TestCache_GenerateKey(t *testing.T) {
	cache := NewCache("localhost:6379", "", 15*time.Minute)
	defer cache.Close()

	tests := []struct {
		cloudID   string
		accountID string
		issueID   string
		expected  string
	}{
		{
			cloudID:   "abc123-def-456",
			accountID: "5b10ac8d82e05b22cc7d4ef5",
			issueID:   "10001",
			expected:  "perm:abc123-def-456:5b10ac8d82e05b22cc7d4ef5:10001",
		},
		{
			cloudID:   "test",
			accountID: "user1",
			issueID:   "1",
			expected:  "perm:test:user1:1",
		},
	}

	for _, tt := range tests {
		result := cache.GenerateKey(tt.cloudID, tt.accountID, tt.issueID)
		if result != tt.expected {
			t.Errorf("expected key '%s', got '%s'", tt.expected, result)
		}
	}
}

func TestCache_EmptyPermissions(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(mr.Addr(), "", 15*time.Minute)
	defer cache.Close()

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"
	permissions := []string{}

	if err := cache.Set(ctx, key, permissions); err != nil {
		t.Fatalf("failed to set empty permissions: %v", err)
	}

	result, err := cache.Get(ctx, key)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result) != 0 {
		t.Errorf("expected 0 permissions, got %d", len(result))
	}
}

func TestCache_Ping(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(mr.Addr(), "", 15*time.Minute)
	defer cache.Close()

	ctx := context.Background()

	if err := cache.Ping(ctx); err != nil {
		t.Errorf("expected ping to succeed, got %v", err)
	}
}
