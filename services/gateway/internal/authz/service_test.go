package authz

import (
	"context"
	"encoding/base64"
	"errors"
	"testing"

	"github.com/smiskinext/gateway/internal/jira"
)

type mockJiraClient struct {
	checkPermissionsFunc func(ctx context.Context, cloudID, systemToken string, req *jira.PermissionsCheckRequest) ([]string, error)
}

func (m *mockJiraClient) CheckPermissions(ctx context.Context, cloudID, systemToken string, req *jira.PermissionsCheckRequest) ([]string, error) {
	if m.checkPermissionsFunc != nil {
		return m.checkPermissionsFunc(ctx, cloudID, systemToken, req)
	}
	return []string{}, nil
}

type mockCache struct {
	getFunc      func(ctx context.Context, key string) ([]string, error)
	setFunc      func(ctx context.Context, key string, permissions []string) error
	getStaleFunc func(ctx context.Context, key string) ([]string, error)
}

func (m *mockCache) Get(ctx context.Context, key string) ([]string, error) {
	if m.getFunc != nil {
		return m.getFunc(ctx, key)
	}
	return nil, nil
}

func (m *mockCache) Set(ctx context.Context, key string, permissions []string) error {
	if m.setFunc != nil {
		return m.setFunc(ctx, key, permissions)
	}
	return nil
}

func (m *mockCache) GetStale(ctx context.Context, key string) ([]string, error) {
	if m.getStaleFunc != nil {
		return m.getStaleFunc(ctx, key)
	}
	return nil, errors.New("no stale data")
}

func (m *mockCache) GenerateKey(cloudID, accountID, context string) string {
	return "perm:" + cloudID + ":" + accountID + ":" + context
}

func TestAuthorize_CacheHit(t *testing.T) {
	mockJira := &mockJiraClient{}
	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			return []string{"view-meeting", "edit-meeting"}, nil
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		IssueID:     "10001",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 2 {
		t.Errorf("expected 2 permissions, got %d", len(result.Permissions))
	}
}

func TestAuthorize_CacheMiss_JiraSuccess(t *testing.T) {
	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.PermissionsCheckRequest) ([]string, error) {
			return []string{"view-meeting"}, nil
		},
	}

	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			return nil, nil
		},
		setFunc: func(ctx context.Context, key string, permissions []string) error {
			return nil
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		IssueID:     "10001",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 1 || result.Permissions[0] != "view-meeting" {
		t.Errorf("expected [view-meeting], got %v", result.Permissions)
	}
}

func TestAuthorize_JiraFailure_StaleCacheFallback(t *testing.T) {
	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.PermissionsCheckRequest) ([]string, error) {
			return nil, errors.New("jira api timeout")
		},
	}

	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			return nil, nil
		},
		getStaleFunc: func(ctx context.Context, key string) ([]string, error) {
			return []string{"view-meeting"}, nil
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		IssueID:     "10001",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 1 || result.Permissions[0] != "view-meeting" {
		t.Errorf("expected stale cache [view-meeting], got %v", result.Permissions)
	}
}

func TestAuthorize_JiraFailure_NoStaleCache(t *testing.T) {
	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.PermissionsCheckRequest) ([]string, error) {
			return nil, errors.New("jira api timeout")
		},
	}

	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			return nil, nil
		},
		getStaleFunc: func(ctx context.Context, key string) ([]string, error) {
			return nil, errors.New("no stale data")
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		IssueID:     "10001",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 0 {
		t.Errorf("expected empty permissions, got %v", result.Permissions)
	}
}

func TestAuthorize_NoContextHeaders(t *testing.T) {
	mockJira := &mockJiraClient{}
	mockCacheImpl := &mockCache{}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 0 {
		t.Errorf("expected empty permissions when no context, got %v", result.Permissions)
	}

	if result.CloudID == "" || result.AccountID == "" {
		t.Errorf("expected cloudID and accountID to be extracted")
	}
}

func TestAuthorize_ProjectKeyContext(t *testing.T) {
	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.PermissionsCheckRequest) ([]string, error) {
			if req.ProjectKey != "PROJ" {
				t.Errorf("expected projectKey=PROJ, got %s", req.ProjectKey)
			}
			return []string{"edit-meeting"}, nil
		},
	}

	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			return nil, nil
		},
		setFunc: func(ctx context.Context, key string, permissions []string) error {
			return nil
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		ProjectKey:  "PROJ",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 1 || result.Permissions[0] != "edit-meeting" {
		t.Errorf("expected [edit-meeting], got %v", result.Permissions)
	}
}

func TestAuthorize_MissingSystemToken(t *testing.T) {
	mockJira := &mockJiraClient{}
	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			return nil, nil
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken: validFIT,
		IssueID:  "10001",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 0 {
		t.Errorf("expected empty permissions when system token missing, got %v", result.Permissions)
	}
}

// createValidFIT builds a token matching the documented Forge Invocation Token
// payload shape: nested app/context objects, top-level principal, and the
// literal "forge/invocation-token" issuer.
func createValidFIT() string {
	payload := `{
		"app": {
			"id": "ari:cloud:ecosystem::app/12345",
			"installationId": "ari:cloud:ecosystem::installation/test",
			"apiBaseUrl": "https://api.atlassian.com/ex/jira/abc123-def456",
			"appVersion": "1.0.0"
		},
		"context": {"cloudId": "abc123-def456"},
		"principal": "655362:612c5d42-ac0a-4f00-6f14-9d84a1b2c3d4",
		"aud": "ari:cloud:ecosystem::app/12345",
		"iss": "forge/invocation-token",
		"iat": 1700175149,
		"exp": 1700175174
	}`

	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"RS256","typ":"JWT"}`))
	claims := base64.RawURLEncoding.EncodeToString([]byte(payload))

	return header + "." + claims + ".signature"
}
