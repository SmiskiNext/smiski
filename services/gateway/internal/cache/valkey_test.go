package cache

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
)

func testOptions(addr string, ttl time.Duration) Options {
	return Options{
		Addr:           addr,
		TTL:            ttl,
		StaleRetention: 24 * time.Hour,
	}
}

func newTestCache(t *testing.T, addr string, ttl time.Duration) *Cache {
	t.Helper()

	cache := NewCache(testOptions(addr, ttl))
	t.Cleanup(func() {
		if err := cache.Close(); err != nil {
			t.Errorf("failed to close cache: %v", err)
		}
	})

	return cache
}

func TestCache_GetExisting(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

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

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

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

	cache := newTestCache(t, mr.Addr(), 1*time.Second)

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

func TestCache_SetStoresJSONArrayWithConfiguredTTL(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

	key := "perm:cloud456:user123:10001"
	if err := cache.Set(context.Background(), key, []string{"view-meeting", "edit-meeting"}); err != nil {
		t.Fatalf("failed to set cache: %v", err)
	}

	stored, err := mr.Get(key)
	if err != nil {
		t.Fatalf("failed to read stored value: %v", err)
	}

	if stored != `["view-meeting","edit-meeting"]` {
		t.Errorf("expected a JSON array payload, got %s", stored)
	}

	if ttl := mr.TTL(key); ttl != 15*time.Minute {
		t.Errorf("expected a 15m TTL on the primary key, got %s", ttl)
	}
}

func TestCache_GetStaleSurvivesPrimaryExpiry(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := newTestCache(t, mr.Addr(), 1*time.Second)

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"
	permissions := []string{"view-meeting"}

	if err := cache.Set(ctx, key, permissions); err != nil {
		t.Fatalf("failed to set cache: %v", err)
	}

	mr.FastForward(2 * time.Second)

	fresh, err := cache.Get(ctx, key)
	if err != nil {
		t.Fatalf("expected no error for expired key, got %v", err)
	}

	if fresh != nil {
		t.Errorf("expected the primary entry to expire, got %v", fresh)
	}

	stale, err := cache.GetStale(ctx, key)
	if err != nil {
		t.Fatalf("expected stale permissions to remain available, got %v", err)
	}

	if len(stale) != 1 || stale[0] != "view-meeting" {
		t.Errorf("unexpected stale permissions: %v", stale)
	}
}

func TestCache_GetStaleExpiresAfterRetention(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(Options{
		Addr:           mr.Addr(),
		TTL:            1 * time.Second,
		StaleRetention: 10 * time.Second,
	})
	t.Cleanup(func() {
		if err := cache.Close(); err != nil {
			t.Errorf("failed to close cache: %v", err)
		}
	})

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"

	if err := cache.Set(ctx, key, []string{"view-meeting"}); err != nil {
		t.Fatalf("failed to set cache: %v", err)
	}

	mr.FastForward(11 * time.Second)

	if _, err := cache.GetStale(ctx, key); err == nil {
		t.Error("expected an error once the stale retention window has passed, got nil")
	}
}

func TestCache_GetStaleMissing(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

	if _, err := cache.GetStale(context.Background(), "perm:cloud1:user1:issue1"); err == nil {
		t.Error("expected an error for a missing stale key, got nil")
	}
}

func TestCache_ConnectionFailureBypass(t *testing.T) {
	cache := newTestCache(t, "invalid:6379", 15*time.Minute)

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"

	_, err := cache.Get(ctx, key)
	if err == nil {
		t.Fatal("expected error for connection failure, got nil")
	}
}

func TestCache_GenerateKey(t *testing.T) {
	cache := newTestCache(t, "localhost:6379", 15*time.Minute)

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

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"
	permissions := []string{}

	if err := cache.Set(ctx, key, permissions); err != nil {
		t.Fatalf("failed to set empty permissions: %v", err)
	}

	stored, err := mr.Get(key)
	if err != nil {
		t.Fatalf("failed to read stored value: %v", err)
	}

	if stored != "[]" {
		t.Errorf("expected an empty JSON array, got %s", stored)
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

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

	ctx := context.Background()

	if err := cache.Ping(ctx); err != nil {
		t.Errorf("expected ping to succeed, got %v", err)
	}
}

func TestCache_PingConnectionFailure(t *testing.T) {
	cache := newTestCache(t, "invalid:6379", 15*time.Minute)

	if err := cache.Ping(context.Background()); err == nil {
		t.Error("expected ping to fail for an unreachable server, got nil")
	}
}

func TestCache_GetStaleReturnsStoredValue(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"
	permissions := []string{"view-meeting", "edit-meeting"}

	if err := cache.Set(ctx, key, permissions); err != nil {
		t.Fatalf("failed to set cache: %v", err)
	}

	result, err := cache.GetStale(ctx, key)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result) != 2 || result[0] != "view-meeting" || result[1] != "edit-meeting" {
		t.Errorf("unexpected permissions: %v", result)
	}
}

func TestCache_GetStaleMalformedPayload(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

	key := "perm:cloud1:user1:issue1"
	if err := mr.Set(key+staleKeySuffix, "not json"); err != nil {
		t.Fatalf("failed to seed malformed payload: %v", err)
	}

	if _, err := cache.GetStale(context.Background(), key); err == nil {
		t.Error("expected unmarshal error for a malformed payload, got nil")
	}
}

func TestCache_GetMalformedPayload(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

	key := "perm:cloud1:user1:issue1"
	if err := mr.Set(key, "not json"); err != nil {
		t.Fatalf("failed to seed malformed payload: %v", err)
	}

	if _, err := cache.Get(context.Background(), key); err == nil {
		t.Error("expected unmarshal error for a malformed payload, got nil")
	}
}

func TestCache_SetConnectionFailure(t *testing.T) {
	cache := newTestCache(t, "invalid:6379", 15*time.Minute)

	if err := cache.Set(context.Background(), "perm:cloud1:user1:issue1", []string{"view-meeting"}); err == nil {
		t.Error("expected set to fail for an unreachable server, got nil")
	}
}

func TestCache_RecoversAfterOutage(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := newTestCache(t, mr.Addr(), 15*time.Minute)

	ctx := context.Background()
	key := "perm:cloud1:user1:issue1"

	if err := cache.Set(ctx, key, []string{"view-meeting"}); err != nil {
		t.Fatalf("failed to set cache before the outage: %v", err)
	}

	mr.Close()

	if _, err := cache.Get(ctx, key); err == nil {
		t.Error("expected an error while the server is unreachable, got nil")
	}

	if err := mr.Restart(); err != nil {
		t.Fatalf("failed to restart the server: %v", err)
	}

	if err := cache.Set(ctx, key, []string{"edit-meeting"}); err != nil {
		t.Errorf("expected the cache to reconnect after recovery, got %v", err)
	}
}

func TestCache_TLSConnectionFailsAgainstPlaintextServer(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(Options{
		Addr:           mr.Addr(),
		TLS:            true,
		TTL:            15 * time.Minute,
		StaleRetention: 24 * time.Hour,
	})
	t.Cleanup(func() {
		if err := cache.Close(); err != nil {
			t.Errorf("failed to close cache: %v", err)
		}
	})

	if err := cache.Ping(context.Background()); err == nil {
		t.Error("expected a TLS handshake failure against a plaintext server, got nil")
	}
}

func TestCache_CredentialProviderSuppliesCredentials(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	mr.RequireUserAuth("gateway", "rotating-secret")

	cache := NewCache(testOptions(mr.Addr(), 15*time.Minute))
	t.Cleanup(func() {
		if err := cache.Close(); err != nil {
			t.Errorf("failed to close cache: %v", err)
		}
	})

	var observedAddress string
	cache.WithCredentialProvider(func(ctx CredentialContext) (Credentials, error) {
		observedAddress = ctx.Address
		return Credentials{Username: "gateway", Password: "rotating-secret"}, nil
	})

	if err := cache.Ping(context.Background()); err != nil {
		t.Fatalf("expected the provided credentials to authenticate, got %v", err)
	}

	if observedAddress == "" {
		t.Error("expected the credential provider to receive the connection address")
	}
}

func TestCache_CredentialProviderErrorFailsConnection(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(testOptions(mr.Addr(), 15*time.Minute))
	t.Cleanup(func() {
		if err := cache.Close(); err != nil {
			t.Errorf("failed to close cache: %v", err)
		}
	})

	cache.WithCredentialProvider(func(CredentialContext) (Credentials, error) {
		return Credentials{}, errors.New("token generation failed")
	})

	if err := cache.Ping(context.Background()); err == nil {
		t.Error("expected the connection to fail when credentials cannot be resolved, got nil")
	}
}

func TestCache_NewCacheDoesNotConnect(t *testing.T) {
	cache := NewCache(testOptions("invalid:6379", 15*time.Minute))
	t.Cleanup(func() {
		if err := cache.Close(); err != nil {
			t.Errorf("failed to close cache: %v", err)
		}
	})

	if cache.client != nil {
		t.Error("expected construction to defer connecting until first use")
	}
}

func TestCache_CloseIsIdempotent(t *testing.T) {
	mr := miniredis.RunT(t)
	defer mr.Close()

	cache := NewCache(testOptions(mr.Addr(), 15*time.Minute))

	if err := cache.Ping(context.Background()); err != nil {
		t.Fatalf("expected ping to succeed, got %v", err)
	}

	if err := cache.Close(); err != nil {
		t.Fatalf("expected the first close to succeed, got %v", err)
	}

	if err := cache.Close(); err != nil {
		t.Errorf("expected a repeated close to be a no-op, got %v", err)
	}
}
