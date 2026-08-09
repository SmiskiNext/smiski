package authz

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"testing"

	"github.com/smiskinext/gateway/internal/jira"
)

// serializingCache mimics Valkey by storing the JSON encoding of the cached
// permissions, so a nil slice round-trips as null exactly as it would in
// production and cannot silently satisfy the cache-hit check.
type serializingCache struct {
	entries map[string]string
}

func newSerializingCache() *serializingCache {
	return &serializingCache{entries: map[string]string{}}
}

func (c *serializingCache) Get(_ context.Context, key string) ([]string, error) {
	stored, present := c.entries[key]
	if !present {
		return nil, nil
	}

	var permissions []string
	if err := json.Unmarshal([]byte(stored), &permissions); err != nil {
		return nil, err
	}

	return permissions, nil
}

func (c *serializingCache) Set(_ context.Context, key string, permissions []string) error {
	encoded, err := json.Marshal(permissions)
	if err != nil {
		return err
	}

	c.entries[key] = string(encoded)

	return nil
}

func (c *serializingCache) GetStale(_ context.Context, key string) ([]string, error) {
	return nil, errors.New("no stale data for " + key)
}

func (c *serializingCache) GenerateKey(cloudID, accountID, context string) string {
	return "perm:" + cloudID + ":" + accountID + ":" + context
}

type mockJiraClient struct {
	checkPermissionsFunc func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error)
}

func (m *mockJiraClient) CheckPermissions(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
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
	viewARI := "ari:cloud:ecosystem::extension/12345/67890/static/view-meeting"

	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			return []string{viewARI}, nil
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
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
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
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
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

// TestAuthorize_UserlessFITAllowedWithEmptyAccountID covers the app life-cycle
// path: a FIT carrying no principal, sent without context headers, as the Forge
// install/upgrade trigger and pre-uninstall function do. The tenant is resolved
// from the cloudId, the accountId stays empty, and the request is not denied.
func TestAuthorize_UserlessFITAllowedWithEmptyAccountID(t *testing.T) {
	jiraCalled := false
	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			jiraCalled = true
			return nil, errors.New("permission check must not run for a context-free request")
		},
	}

	service := NewAuthzService(mockJira, &mockCache{})

	req := &AuthzRequest{
		FITToken:    createUserlessFIT(),
		SystemToken: "system-token",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error for a user-less FIT, got %v", err)
	}

	if result.CloudID != "abc123-def456" {
		t.Errorf("expected cloudId %q, got %q", "abc123-def456", result.CloudID)
	}

	if result.AccountID != "" {
		t.Errorf("expected an empty accountId, got %q", result.AccountID)
	}

	if len(result.Permissions) != 0 {
		t.Errorf("expected empty permissions when no context, got %v", result.Permissions)
	}

	if jiraCalled {
		t.Error("expected the Jira permission check to be skipped")
	}
}

func TestAuthorize_ProjectIDContext(t *testing.T) {
	editARI := "ari:cloud:ecosystem::extension/12345/67890/static/edit-meeting"

	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			if len(req.ProjectPermissions) == 0 {
				t.Error("expected projectPermissions to be present")
				return nil, errors.New("no project permissions")
			}
			if len(req.ProjectPermissions[0].Projects) == 0 || req.ProjectPermissions[0].Projects[0] != 10002 {
				t.Errorf("expected projects=[10002], got %v", req.ProjectPermissions[0].Projects)
			}
			return []string{editARI}, nil
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
		ProjectID:   "10002",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 1 || result.Permissions[0] != "edit-meeting" {
		t.Errorf("expected [edit-meeting], got %v", result.Permissions)
	}
}

func TestAuthorize_IssueContextPreferred(t *testing.T) {
	viewARI := "ari:cloud:ecosystem::extension/12345/67890/static/view-meeting"

	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			if len(req.ProjectPermissions) == 0 {
				t.Error("expected projectPermissions to be present")
				return nil, errors.New("no project permissions")
			}
			if len(req.ProjectPermissions[0].Issues) == 0 || req.ProjectPermissions[0].Issues[0] != 10001 {
				t.Errorf("expected issues=[10001], got %v", req.ProjectPermissions[0].Issues)
			}
			if len(req.ProjectPermissions[0].Projects) != 0 {
				t.Errorf("expected projects to be empty when issue is present, got %v", req.ProjectPermissions[0].Projects)
			}
			return []string{viewARI}, nil
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
		ProjectID:   "10002",
	}

	result, err := service.Authorize(context.Background(), req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(result.Permissions) != 1 {
		t.Errorf("expected 1 permission, got %d", len(result.Permissions))
	}
}

func TestAuthorize_NonNumericIssueID(t *testing.T) {
	mockJira := &mockJiraClient{}
	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			return nil, nil
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		IssueID:     "SMISKI-101",
	}

	_, err := service.Authorize(context.Background(), req)
	if err == nil {
		t.Fatal("expected error for non-numeric issue ID, got nil")
	}
}

func TestAuthorize_NonNumericProjectID(t *testing.T) {
	mockJira := &mockJiraClient{}
	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			return nil, nil
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)

	validFIT := createValidFIT()
	req := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		ProjectID:   "SMISKI",
	}

	_, err := service.Authorize(context.Background(), req)
	if err == nil {
		t.Fatal("expected error for non-numeric project ID, got nil")
	}
}

func TestAuthorize_CacheKeyDistinctForIssueAndProject(t *testing.T) {
	var cacheKeys []string

	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			return []string{}, nil
		},
	}

	mockCacheImpl := &mockCache{
		getFunc: func(ctx context.Context, key string) ([]string, error) {
			cacheKeys = append(cacheKeys, key)
			return nil, nil
		},
		setFunc: func(ctx context.Context, key string, permissions []string) error {
			return nil
		},
	}

	service := NewAuthzService(mockJira, mockCacheImpl)
	validFIT := createValidFIT()

	req1 := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		IssueID:     "10001",
	}

	req2 := &AuthzRequest{
		FITToken:    validFIT,
		SystemToken: "system-token",
		ProjectID:   "10001",
	}

	_, _ = service.Authorize(context.Background(), req1)
	_, _ = service.Authorize(context.Background(), req2)

	if len(cacheKeys) != 2 {
		t.Fatalf("expected 2 cache lookups, got %d", len(cacheKeys))
	}

	if cacheKeys[0] == cacheKeys[1] {
		t.Errorf("expected distinct cache keys for issue vs project with same numeric value, both were: %s", cacheKeys[0])
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
	if err == nil {
		t.Fatal("expected an error when the system token is missing, got nil")
	}

	if !errors.Is(err, ErrMissingSystemToken) {
		t.Errorf("expected the error to be identifiable as ErrMissingSystemToken, got %v", err)
	}

	if result != nil {
		t.Errorf("expected no result alongside the configuration error, got %v", result)
	}
}

func TestAuthorize_UnrecognizedPermissionIgnored(t *testing.T) {
	viewARI := "ari:cloud:ecosystem::extension/12345/67890/static/view-meeting"
	unknownARI := "ari:cloud:ecosystem::extension/12345/67890/static/unknown-perm"

	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			return []string{viewARI, unknownARI}, nil
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
		t.Errorf("expected only [view-meeting], got %v", result.Permissions)
	}
}

func TestAuthorize_MissingAppEnvironmentClaims(t *testing.T) {
	mockJira := &mockJiraClient{}
	mockCacheImpl := &mockCache{}

	service := NewAuthzService(mockJira, mockCacheImpl)

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
	fitWithoutEnv := header + "." + claims + ".signature"

	req := &AuthzRequest{
		FITToken:    fitWithoutEnv,
		SystemToken: "system-token",
		IssueID:     "10001",
	}

	_, err := service.Authorize(context.Background(), req)
	if err == nil {
		t.Fatal("expected error for missing app.environment.id, got nil")
	}
}

func TestAuthorize_EmptyPermissionsCachedAsJSONArray(t *testing.T) {
	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			return []string{}, nil
		},
	}

	cacheImpl := newSerializingCache()
	service := NewAuthzService(mockJira, cacheImpl)

	req := &AuthzRequest{
		FITToken:    createValidFIT(),
		SystemToken: "system-token",
		IssueID:     "10001",
	}

	if _, err := service.Authorize(context.Background(), req); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(cacheImpl.entries) != 1 {
		t.Fatalf("expected exactly 1 cache entry, got %d", len(cacheImpl.entries))
	}

	for key, stored := range cacheImpl.entries {
		if stored != "[]" {
			t.Errorf("key %s: expected an empty JSON array, got %s", key, stored)
		}
	}
}

func TestAuthorize_NoPermissionUserSecondRequestIsCacheHit(t *testing.T) {
	jiraCalls := 0

	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			jiraCalls++
			return []string{}, nil
		},
	}

	service := NewAuthzService(mockJira, newSerializingCache())

	validFIT := createValidFIT()
	newRequest := func() *AuthzRequest {
		return &AuthzRequest{
			FITToken:    validFIT,
			SystemToken: "system-token",
			IssueID:     "10001",
		}
	}

	if _, err := service.Authorize(context.Background(), newRequest()); err != nil {
		t.Fatalf("expected no error on the first request, got %v", err)
	}

	result, err := service.Authorize(context.Background(), newRequest())
	if err != nil {
		t.Fatalf("expected no error on the second request, got %v", err)
	}

	if len(result.Permissions) != 0 {
		t.Errorf("expected empty permissions, got %v", result.Permissions)
	}

	if jiraCalls != 1 {
		t.Errorf("expected the cached empty result to be a hit, so Jira is called exactly once, got %d calls", jiraCalls)
	}
}

func TestAuthorize_ZeroIssueIDScopesTheRequest(t *testing.T) {
	var captured *jira.BulkPermissionsRequestBean

	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			captured = req
			return []string{}, nil
		},
	}

	service := NewAuthzService(mockJira, newSerializingCache())

	req := &AuthzRequest{
		FITToken:    createValidFIT(),
		SystemToken: "system-token",
		IssueID:     "0",
	}

	if _, err := service.Authorize(context.Background(), req); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if captured == nil {
		t.Fatal("expected the Jira client to be called")
	}

	if len(captured.ProjectPermissions) != 1 {
		t.Fatalf("expected exactly 1 projectPermissions entry, got %d", len(captured.ProjectPermissions))
	}

	entry := captured.ProjectPermissions[0]
	if len(entry.Issues) != 1 || entry.Issues[0] != 0 {
		t.Errorf("expected issues=[0] so the query stays scoped, got %v", entry.Issues)
	}

	if len(entry.Projects) != 0 {
		t.Errorf("expected projects to be omitted for an issue context, got %v", entry.Projects)
	}
}

func TestAuthorize_ZeroProjectIDScopesTheRequest(t *testing.T) {
	var captured *jira.BulkPermissionsRequestBean

	mockJira := &mockJiraClient{
		checkPermissionsFunc: func(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error) {
			captured = req
			return []string{}, nil
		},
	}

	service := NewAuthzService(mockJira, newSerializingCache())

	req := &AuthzRequest{
		FITToken:    createValidFIT(),
		SystemToken: "system-token",
		ProjectID:   "0",
	}

	if _, err := service.Authorize(context.Background(), req); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if captured == nil {
		t.Fatal("expected the Jira client to be called")
	}

	if len(captured.ProjectPermissions) != 1 {
		t.Fatalf("expected exactly 1 projectPermissions entry, got %d", len(captured.ProjectPermissions))
	}

	entry := captured.ProjectPermissions[0]
	if len(entry.Projects) != 1 || entry.Projects[0] != 0 {
		t.Errorf("expected projects=[0] so the query stays scoped, got %v", entry.Projects)
	}

	if len(entry.Issues) != 0 {
		t.Errorf("expected issues to be omitted for a project context, got %v", entry.Issues)
	}
}

func TestMapARIsToBareKeys_NoMatchReturnsEmptySlice(t *testing.T) {
	bareKeys := mapARIsToBareKeys([]string{"ari:cloud:ecosystem::extension/other/env/static/view-meeting"}, "12345", "67890")

	if bareKeys == nil {
		t.Fatal("expected a non-nil empty slice so the cached value serialises as [] rather than null")
	}

	encoded, err := json.Marshal(bareKeys)
	if err != nil {
		t.Fatalf("failed to marshal bare keys: %v", err)
	}

	if string(encoded) != "[]" {
		t.Errorf("expected the empty result to serialise as [], got %s", encoded)
	}
}

func createValidFIT() string {
	payload := `{
		"app": {
			"id": "ari:cloud:ecosystem::app/12345",
			"installationId": "ari:cloud:ecosystem::installation/test",
			"apiBaseUrl": "https://api.atlassian.com/ex/jira/abc123-def456",
			"appVersion": "1.0.0",
			"environment": {
				"id": "ari:cloud:ecosystem::environment/67890"
			}
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

// createUserlessFIT builds the token an app-level invocation carries: the same
// claims as createValidFIT minus the principal, which Atlassian omits for a
// Forge lifecycle trigger or pre-uninstall function.
func createUserlessFIT() string {
	payload := `{
		"app": {
			"id": "ari:cloud:ecosystem::app/12345",
			"installationId": "ari:cloud:ecosystem::installation/test",
			"apiBaseUrl": "https://api.atlassian.com/ex/jira/abc123-def456",
			"appVersion": "1.0.0",
			"environment": {
				"id": "ari:cloud:ecosystem::environment/67890"
			}
		},
		"context": {"cloudId": "abc123-def456"},
		"aud": "ari:cloud:ecosystem::app/12345",
		"iss": "forge/invocation-token",
		"iat": 1700175149,
		"exp": 1700175174
	}`

	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"RS256","typ":"JWT"}`))
	claims := base64.RawURLEncoding.EncodeToString([]byte(payload))

	return header + "." + claims + ".signature"
}
