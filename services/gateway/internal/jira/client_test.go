package jira

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestCheckPermissions_Success(t *testing.T) {
	response := PermissionsCheckResponse{
		ProjectPermissions: []PermissionCheckResult{
			{Key: "view-meeting", HasPermission: true},
			{Key: "edit-meeting", HasPermission: true},
		},
	}

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}

		if auth := r.Header.Get("Authorization"); auth != "Bearer test-token" {
			t.Errorf("expected Authorization header 'Bearer test-token', got '%s'", auth)
		}

		if ct := r.Header.Get("Content-Type"); ct != "application/json" {
			t.Errorf("expected Content-Type 'application/json', got '%s'", ct)
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.baseURL = server.URL

	req := &PermissionsCheckRequest{
		AccountID:   "test-account",
		Permissions: []string{"view-meeting", "edit-meeting"},
		IssueID:     "10001",
	}

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token", req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(permissions) != 2 {
		t.Errorf("expected 2 permissions, got %d", len(permissions))
	}

	if permissions[0] != "view-meeting" || permissions[1] != "edit-meeting" {
		t.Errorf("unexpected permissions: %v", permissions)
	}
}

func TestCheckPermissions_PartialPermissions(t *testing.T) {
	response := PermissionsCheckResponse{
		ProjectPermissions: []PermissionCheckResult{
			{Key: "view-meeting", HasPermission: true},
			{Key: "edit-meeting", HasPermission: false},
		},
	}

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.baseURL = server.URL

	req := &PermissionsCheckRequest{
		AccountID:   "test-account",
		Permissions: []string{"view-meeting", "edit-meeting"},
		IssueID:     "10001",
	}

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token", req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(permissions) != 1 {
		t.Errorf("expected 1 permission, got %d", len(permissions))
	}

	if permissions[0] != "view-meeting" {
		t.Errorf("expected 'view-meeting', got '%s'", permissions[0])
	}
}

func TestCheckPermissions_NoPermissions(t *testing.T) {
	response := PermissionsCheckResponse{
		ProjectPermissions: []PermissionCheckResult{
			{Key: "view-meeting", HasPermission: false},
			{Key: "edit-meeting", HasPermission: false},
		},
	}

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.baseURL = server.URL

	req := &PermissionsCheckRequest{
		AccountID:   "test-account",
		Permissions: []string{"view-meeting", "edit-meeting"},
		IssueID:     "10001",
	}

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token", req)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(permissions) != 0 {
		t.Errorf("expected 0 permissions, got %d", len(permissions))
	}
}

func TestCheckPermissions_Timeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(3 * time.Second)
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.baseURL = server.URL
	client.timeout = 100 * time.Millisecond

	req := &PermissionsCheckRequest{
		AccountID:   "test-account",
		Permissions: []string{"view-meeting"},
		IssueID:     "10001",
	}

	ctx := context.Background()
	_, err := client.CheckPermissions(ctx, "", "test-token", req)
	if err == nil {
		t.Fatal("expected timeout error, got nil")
	}
}

func TestCheckPermissions_RateLimitRetry(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts < 3 {
			w.WriteHeader(http.StatusTooManyRequests)
			return
		}

		response := PermissionsCheckResponse{
			ProjectPermissions: []PermissionCheckResult{
				{Key: "view-meeting", HasPermission: true},
			},
		}
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.baseURL = server.URL

	req := &PermissionsCheckRequest{
		AccountID:   "test-account",
		Permissions: []string{"view-meeting"},
		IssueID:     "10001",
	}

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token", req)
	if err != nil {
		t.Fatalf("expected no error after retries, got %v", err)
	}

	if len(permissions) != 1 {
		t.Errorf("expected 1 permission, got %d", len(permissions))
	}

	if attempts != 3 {
		t.Errorf("expected 3 attempts, got %d", attempts)
	}
}

func TestCheckPermissions_ServerError(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("Internal Server Error"))
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.baseURL = server.URL

	req := &PermissionsCheckRequest{
		AccountID:   "test-account",
		Permissions: []string{"view-meeting"},
		IssueID:     "10001",
	}

	_, err := client.CheckPermissions(context.Background(), "", "test-token", req)
	if err == nil {
		t.Fatal("expected error for server error, got nil")
	}

	if attempts != 4 {
		t.Errorf("expected 4 attempts (1 initial + 3 retries), got %d", attempts)
	}
}

func TestCheckPermissions_BadRequest(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		errResp := ErrorResponse{
			ErrorMessages: []string{"Invalid request"},
		}
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(errResp)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.baseURL = server.URL

	req := &PermissionsCheckRequest{
		AccountID:   "test-account",
		Permissions: []string{"view-meeting"},
		IssueID:     "10001",
	}

	_, err := client.CheckPermissions(context.Background(), "", "test-token", req)
	if err == nil {
		t.Fatal("expected error for bad request, got nil")
	}

	if attempts != 1 {
		t.Errorf("expected 1 attempt (no retry for 400), got %d", attempts)
	}
}

func TestExtractGrantedPermissions(t *testing.T) {
	resp := &PermissionsCheckResponse{
		GlobalPermissions: []PermissionCheckResult{
			{Key: "global-perm", HasPermission: true},
		},
		ProjectPermissions: []PermissionCheckResult{
			{Key: "view-meeting", HasPermission: true},
			{Key: "edit-meeting", HasPermission: false},
			{Key: "delete-meeting", HasPermission: true},
		},
	}

	granted := extractGrantedPermissions(resp)

	if len(granted) != 3 {
		t.Errorf("expected 3 granted permissions, got %d", len(granted))
	}

	expected := map[string]bool{
		"global-perm":    true,
		"view-meeting":   true,
		"delete-meeting": true,
	}

	for _, perm := range granted {
		if !expected[perm] {
			t.Errorf("unexpected permission: %s", perm)
		}
	}
}
