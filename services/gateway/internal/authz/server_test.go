package authz

import (
	"context"
	"encoding/json"
	"fmt"
	"testing"

	envoy_service_auth_v3 "github.com/envoyproxy/go-control-plane/envoy/service/auth/v3"
	"google.golang.org/grpc/codes"
)

type mockAuthzService struct {
	authorizeFunc func(ctx context.Context, req *AuthzRequest) (*AuthzResult, error)
}

func (m *mockAuthzService) Authorize(ctx context.Context, req *AuthzRequest) (*AuthzResult, error) {
	if m.authorizeFunc != nil {
		return m.authorizeFunc(ctx, req)
	}
	return &AuthzResult{
		CloudID:     "test-cloud",
		AccountID:   "test-account",
		Permissions: []string{"view-meeting"},
	}, nil
}

func TestCheck_ValidAuthorization(t *testing.T) {
	mockAuthz := &mockAuthzService{
		authorizeFunc: func(ctx context.Context, req *AuthzRequest) (*AuthzResult, error) {
			return &AuthzResult{
				CloudID:     "abc123",
				AccountID:   "user456",
				Permissions: []string{"view-meeting", "edit-meeting"},
			}, nil
		},
	}

	server := NewServer(mockAuthz)

	checkReq := &envoy_service_auth_v3.CheckRequest{
		Attributes: &envoy_service_auth_v3.AttributeContext{
			Request: &envoy_service_auth_v3.AttributeContext_Request{
				Http: &envoy_service_auth_v3.AttributeContext_HttpRequest{
					Headers: map[string]string{
						"authorization":        "Bearer test-token",
						"x-issue-id":           "10001",
						"x-forge-oauth-system": "system-token",
					},
				},
			},
		},
	}

	resp, err := server.Check(context.Background(), checkReq)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if resp.Status.Code != int32(codes.OK) {
		t.Errorf("expected status OK, got %v", resp.Status.Code)
	}

	okResp := resp.GetOkResponse()
	if okResp == nil {
		t.Fatal("expected OkResponse, got nil")
	}

	headers := okResp.Headers
	if len(headers) != 3 {
		t.Errorf("expected 3 headers, got %d", len(headers))
	}

	var foundTenantID, foundAccountID, foundPermissions bool
	for _, h := range headers {
		switch h.Header.Key {
		case "x-tenant-id":
			if h.Header.Value != "abc123" {
				t.Errorf("expected x-tenant-id=abc123, got %s", h.Header.Value)
			}
			foundTenantID = true
		case "x-account-id":
			if h.Header.Value != "user456" {
				t.Errorf("expected x-account-id=user456, got %s", h.Header.Value)
			}
			foundAccountID = true
		case "x-project-permissions":
			if h.Header.Value != "view-meeting,edit-meeting" {
				t.Errorf("expected permissions=view-meeting,edit-meeting, got %s", h.Header.Value)
			}
			foundPermissions = true
		}
	}

	if !foundTenantID || !foundAccountID || !foundPermissions {
		t.Error("missing expected headers")
	}
}

// TestCheck_EmptyAccountIDStillAllowed pins the response an app life-cycle
// invocation receives: a resolvable cloudId is enough for an allow decision, so
// x-account-id is injected with an empty value rather than blocking the
// response.
func TestCheck_EmptyAccountIDStillAllowed(t *testing.T) {
	mockAuthz := &mockAuthzService{
		authorizeFunc: func(ctx context.Context, req *AuthzRequest) (*AuthzResult, error) {
			return &AuthzResult{
				CloudID:     "abc123",
				AccountID:   "",
				Permissions: []string{},
			}, nil
		},
	}

	server := NewServer(mockAuthz)

	checkReq := &envoy_service_auth_v3.CheckRequest{
		Attributes: &envoy_service_auth_v3.AttributeContext{
			Request: &envoy_service_auth_v3.AttributeContext_Request{
				Http: &envoy_service_auth_v3.AttributeContext_HttpRequest{
					Headers: map[string]string{
						"authorization": "Bearer userless-token",
					},
				},
			},
		},
	}

	resp, err := server.Check(context.Background(), checkReq)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if resp.Status.Code != int32(codes.OK) {
		t.Errorf("expected status OK, got %v", resp.Status.Code)
	}

	okResp := resp.GetOkResponse()
	if okResp == nil {
		t.Fatal("expected OkResponse, got nil")
	}

	values := map[string]string{}
	for _, h := range okResp.Headers {
		values[h.Header.Key] = h.Header.Value
	}

	if values["x-tenant-id"] != "abc123" {
		t.Errorf("expected x-tenant-id=abc123, got %q", values["x-tenant-id"])
	}

	accountID, present := values["x-account-id"]
	if !present {
		t.Error("expected x-account-id to be injected even when empty")
	}

	if accountID != "" {
		t.Errorf("expected an empty x-account-id, got %q", accountID)
	}

	permissions, present := values["x-project-permissions"]
	if !present {
		t.Error("expected x-project-permissions to be injected")
	}

	if permissions != "" {
		t.Errorf("expected empty permissions, got %q", permissions)
	}
}

func TestCheck_MissingAuthorizationHeader(t *testing.T) {
	mockAuthz := &mockAuthzService{}
	server := NewServer(mockAuthz)

	checkReq := &envoy_service_auth_v3.CheckRequest{
		Attributes: &envoy_service_auth_v3.AttributeContext{
			Request: &envoy_service_auth_v3.AttributeContext_Request{
				Http: &envoy_service_auth_v3.AttributeContext_HttpRequest{
					Headers: map[string]string{
						"x-issue-id": "10001",
					},
				},
			},
		},
	}

	resp, err := server.Check(context.Background(), checkReq)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if resp.Status.Code != int32(codes.Unauthenticated) {
		t.Errorf("expected status Unauthenticated, got %v", resp.Status.Code)
	}

	deniedResp := resp.GetDeniedResponse()
	if deniedResp == nil {
		t.Fatal("expected DeniedResponse, got nil")
	}

	if deniedResp.Status.Code != 401 {
		t.Errorf("expected HTTP 401, got %d", deniedResp.Status.Code)
	}
}

func TestCheck_AuthorizationFailed(t *testing.T) {
	mockAuthz := &mockAuthzService{
		authorizeFunc: func(ctx context.Context, req *AuthzRequest) (*AuthzResult, error) {
			return nil, fmt.Errorf("invalid token")
		},
	}

	server := NewServer(mockAuthz)

	checkReq := &envoy_service_auth_v3.CheckRequest{
		Attributes: &envoy_service_auth_v3.AttributeContext{
			Request: &envoy_service_auth_v3.AttributeContext_Request{
				Http: &envoy_service_auth_v3.AttributeContext_HttpRequest{
					Headers: map[string]string{
						"authorization":        "Bearer invalid-token",
						"x-forge-oauth-system": "system-token",
					},
				},
			},
		},
	}

	resp, err := server.Check(context.Background(), checkReq)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if resp.Status.Code != int32(codes.PermissionDenied) {
		t.Errorf("expected status PermissionDenied, got %v", resp.Status.Code)
	}

	deniedResp := resp.GetDeniedResponse()
	if deniedResp == nil {
		t.Fatal("expected DeniedResponse, got nil")
	}

	if deniedResp.Status.Code != 403 {
		t.Errorf("expected HTTP 403, got %d", deniedResp.Status.Code)
	}
}

func TestCheck_MissingSystemToken(t *testing.T) {
	mockAuthz := &mockAuthzService{
		authorizeFunc: func(ctx context.Context, req *AuthzRequest) (*AuthzResult, error) {
			if req.SystemToken != "" {
				t.Errorf("expected an empty system token, got %q", req.SystemToken)
			}
			return nil, fmt.Errorf("checking jira permissions: %w", ErrMissingSystemToken)
		},
	}

	server := NewServer(mockAuthz)

	checkReq := &envoy_service_auth_v3.CheckRequest{
		Attributes: &envoy_service_auth_v3.AttributeContext{
			Request: &envoy_service_auth_v3.AttributeContext_Request{
				Http: &envoy_service_auth_v3.AttributeContext_HttpRequest{
					Headers: map[string]string{
						"authorization": "Bearer test-token",
						"x-issue-id":    "10001",
					},
				},
			},
		},
	}

	resp, err := server.Check(context.Background(), checkReq)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if resp.Status.Code != int32(codes.Internal) {
		t.Errorf("expected gRPC status Internal (%d), got %v", int32(codes.Internal), resp.Status.Code)
	}

	if resp.Status.Message != missingSystemTokenMessage {
		t.Errorf("expected gRPC status message %q, got %q", missingSystemTokenMessage, resp.Status.Message)
	}

	deniedResp := resp.GetDeniedResponse()
	if deniedResp == nil {
		t.Fatal("expected DeniedResponse, got nil")
	}

	if deniedResp.Status.Code != 500 {
		t.Errorf("expected HTTP 500, got %d", deniedResp.Status.Code)
	}

	var body map[string]string
	if err := json.Unmarshal([]byte(deniedResp.Body), &body); err != nil {
		t.Fatalf("expected a JSON body, got %q: %v", deniedResp.Body, err)
	}

	if body["error"] != configurationErrorCode {
		t.Errorf("expected error=%q, got %q", configurationErrorCode, body["error"])
	}

	if body["message"] != missingSystemTokenMessage {
		t.Errorf("expected message=%q, got %q", missingSystemTokenMessage, body["message"])
	}
}

func TestCheck_ProjectIDHeaderReachesAuthzRequest(t *testing.T) {
	var captured *AuthzRequest

	mockAuthz := &mockAuthzService{
		authorizeFunc: func(ctx context.Context, req *AuthzRequest) (*AuthzResult, error) {
			captured = req
			return &AuthzResult{
				CloudID:     "abc123",
				AccountID:   "user456",
				Permissions: []string{},
			}, nil
		},
	}

	server := NewServer(mockAuthz)

	checkReq := &envoy_service_auth_v3.CheckRequest{
		Attributes: &envoy_service_auth_v3.AttributeContext{
			Request: &envoy_service_auth_v3.AttributeContext_Request{
				Http: &envoy_service_auth_v3.AttributeContext_HttpRequest{
					Headers: map[string]string{
						"authorization":        "Bearer test-token",
						"x-project-id":         "10002",
						"x-forge-oauth-system": "system-token",
					},
				},
			},
		},
	}

	if _, err := server.Check(context.Background(), checkReq); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if captured == nil {
		t.Fatal("expected the authorization service to be called")
	}

	if captured.ProjectID != "10002" {
		t.Errorf("expected ProjectID=10002 to be carried from the x-project-id header, got %q", captured.ProjectID)
	}

	if captured.IssueID != "" {
		t.Errorf("expected an empty IssueID when no x-issue-id header is sent, got %q", captured.IssueID)
	}
}

func TestGetHeader_CaseInsensitive(t *testing.T) {
	headers := map[string]string{
		"Authorization": "Bearer token",
		"x-issue-id":    "10001",
		"X-Project-Id":  "10002",
	}

	if val := getHeader(headers, "authorization"); val != "Bearer token" {
		t.Errorf("expected to find 'authorization', got %s", val)
	}

	if val := getHeader(headers, "X-Issue-Id"); val != "10001" {
		t.Errorf("expected to find 'x-issue-id', got %s", val)
	}

	if val := getHeader(headers, "x-project-id"); val != "10002" {
		t.Errorf("expected to find 'X-Project-Id', got %s", val)
	}

	if val := getHeader(headers, "missing-header"); val != "" {
		t.Errorf("expected empty string for missing header, got %s", val)
	}
}
