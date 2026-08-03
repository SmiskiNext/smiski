package jira

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func encodeJSON(t *testing.T, w http.ResponseWriter, payload any) {
	t.Helper()

	if err := json.NewEncoder(w).Encode(payload); err != nil {
		t.Errorf("failed to encode response: %v", err)
	}
}

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
		encodeJSON(t, w, response)
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
		encodeJSON(t, w, response)
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
		encodeJSON(t, w, response)
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
		encodeJSON(t, w, response)
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
		if _, err := w.Write([]byte("Internal Server Error")); err != nil {
			t.Errorf("failed to write response: %v", err)
		}
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
		encodeJSON(t, w, errResp)
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

func TestRetryableError_UnwrapReturnsCause(t *testing.T) {
	cause := errors.New("connection reset")
	wrapped := &RetryableError{Err: cause}

	if !errors.Is(wrapped, cause) {
		t.Errorf("expected wrapped error to unwrap to its cause")
	}

	if unwrapped := wrapped.Unwrap(); unwrapped != cause {
		t.Errorf("expected Unwrap to return the cause, got %v", unwrapped)
	}
}

func TestRetryableError_ErrorDelegatesToCause(t *testing.T) {
	wrapped := &RetryableError{Err: errors.New("upstream unavailable")}

	if wrapped.Error() != "upstream unavailable" {
		t.Errorf("expected 'upstream unavailable', got '%s'", wrapped.Error())
	}
}

func TestIsRetryable(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{name: "nil error", err: nil, expected: false},
		{name: "retryable error", err: &RetryableError{Err: errors.New("boom")}, expected: true},
		{name: "deadline exceeded", err: context.DeadlineExceeded, expected: true},
		{name: "plain error", err: errors.New("permanent"), expected: false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := isRetryable(tt.err); got != tt.expected {
				t.Errorf("expected %v, got %v", tt.expected, got)
			}
		})
	}
}

func TestDoRequest_MalformedSuccessBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		if _, err := w.Write([]byte("not json")); err != nil {
			t.Errorf("failed to write response: %v", err)
		}
	}))
	defer server.Close()

	client := NewClient(server.URL)

	_, err := client.doRequest(context.Background(), server.URL, "test-token", &PermissionsCheckRequest{})
	if err == nil {
		t.Fatal("expected unmarshal error, got nil")
	}

	if isRetryable(err) {
		t.Error("expected a non-retryable error for a malformed success body")
	}
}

func TestDoRequest_BadRequestWithoutErrorMessages(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		if _, err := w.Write([]byte("plain failure")); err != nil {
			t.Errorf("failed to write response: %v", err)
		}
	}))
	defer server.Close()

	client := NewClient(server.URL)

	_, err := client.doRequest(context.Background(), server.URL, "test-token", &PermissionsCheckRequest{})
	if err == nil {
		t.Fatal("expected error for bad request, got nil")
	}

	if !strings.Contains(err.Error(), "plain failure") {
		t.Errorf("expected the raw body in the error, got '%s'", err.Error())
	}
}

func TestDoRequest_InvalidURLFailsRequestCreation(t *testing.T) {
	client := NewClient("http://example.invalid")

	_, err := client.doRequest(context.Background(), "://bad-url", "test-token", &PermissionsCheckRequest{})
	if err == nil {
		t.Fatal("expected request creation error, got nil")
	}

	if !strings.Contains(err.Error(), "failed to create request") {
		t.Errorf("expected a request creation error, got '%s'", err.Error())
	}
}

func TestDoRequest_TransportFailureIsRetryable(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
	url := server.URL
	server.Close()

	client := NewClient(url)

	_, err := client.doRequest(context.Background(), url, "test-token", &PermissionsCheckRequest{})
	if err == nil {
		t.Fatal("expected transport error, got nil")
	}

	if !isRetryable(err) {
		t.Errorf("expected a retryable error for a transport failure, got %v", err)
	}
}

func TestCheckPermissions_ContextCancelledDuringBackoff(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := NewClient(server.URL)

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.CheckPermissions(ctx, "", "test-token", &PermissionsCheckRequest{})
	if err == nil {
		t.Fatal("expected error for a cancelled context, got nil")
	}
}
