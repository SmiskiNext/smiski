package fit

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
	"testing"
)

const (
	testCloudID   = "4c822e2f-510f-48b9-b8d0-8419d0932949"
	testAccountID = "312d3308-8954-42b0-aa38-771a10c88656"
	testPrincipal = "655362:312d3308-8954-42b0-aa38-771a10c88656"
	testIssuer    = "forge/invocation-token"
	testAudience  = "ari:cloud:ecosystem::app/test-app-id"
)

func validClaims() Claims {
	return Claims{
		App: App{
			ID:             "ari:cloud:ecosystem::app/test-app-id",
			InstallationID: "ari:cloud:ecosystem::installation/test-installation",
			APIBaseURL:     "https://api.atlassian.com/ex/jira/" + testCloudID,
			AppVersion:     "1.0.0",
			Environment: Environment{
				ID: "ari:cloud:ecosystem::environment/test-env-id",
			},
		},
		Context:   Context{CloudID: testCloudID},
		Principal: testPrincipal,
		Issuer:    testIssuer,
		Audience:  testAudience,
	}
}

func TestParse_ValidToken(t *testing.T) {
	result, err := Parse(createTestToken(validClaims()))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.CloudID != testCloudID {
		t.Errorf("expected cloudId %q, got %q", testCloudID, result.CloudID)
	}

	if result.AccountID != testAccountID {
		t.Errorf("expected accountId %q, got %q", testAccountID, result.AccountID)
	}

	if result.AppID != "test-app-id" {
		t.Errorf("expected appId %q, got %q", "test-app-id", result.AppID)
	}

	if result.EnvironmentID != "test-env-id" {
		t.Errorf("expected environmentId %q, got %q", "test-env-id", result.EnvironmentID)
	}

	if result.Claims.Principal != testPrincipal {
		t.Errorf("expected principal %q, got %q", testPrincipal, result.Claims.Principal)
	}

	if result.Claims.Issuer != testIssuer {
		t.Errorf("expected issuer %q, got %q", testIssuer, result.Claims.Issuer)
	}
}

func TestParse_CloudIDFromContextPreferred(t *testing.T) {
	claims := validClaims()
	claims.Context.CloudID = "context-cloud-id"
	claims.App.APIBaseURL = "https://api.atlassian.com/ex/jira/apibaseurl-cloud-id"

	result, err := Parse(createTestToken(claims))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.CloudID != "context-cloud-id" {
		t.Errorf("expected context.cloudId to win, got %q", result.CloudID)
	}
}

func TestParse_CloudIDFallsBackToAPIBaseURL(t *testing.T) {
	claims := validClaims()
	claims.Context.CloudID = ""

	result, err := Parse(createTestToken(claims))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.CloudID != testCloudID {
		t.Errorf("expected cloudId %q from apiBaseUrl, got %q", testCloudID, result.CloudID)
	}
}

func TestParse_MissingCloudIDSources(t *testing.T) {
	claims := validClaims()
	claims.Context.CloudID = ""
	claims.App.APIBaseURL = ""

	_, err := Parse(createTestToken(claims))
	if err == nil {
		t.Fatal("expected error when both cloudId sources are absent, got nil")
	}

	if !errors.Is(err, ErrMissingClaims) {
		t.Errorf("expected ErrMissingClaims, got %v", err)
	}
}

// TestParse_AbsentPrincipalYieldsEmptyAccountID covers a FIT whose payload omits
// the principal claim entirely, as Atlassian does for an app-lifecycle trigger
// or a pre-uninstall function. Such an invocation authenticates on cloudId
// alone.
func TestParse_AbsentPrincipalYieldsEmptyAccountID(t *testing.T) {
	payload := `{
		"app": {
			"id": "ari:cloud:ecosystem::app/test-app-id",
			"installationId": "ari:cloud:ecosystem::installation/test",
			"apiBaseUrl": "https://api.atlassian.com/ex/jira/` + testCloudID + `",
			"appVersion": "1.0.0",
			"environment": {
				"id": "ari:cloud:ecosystem::environment/test-env-id"
			}
		},
		"context": {"cloudId": "` + testCloudID + `"},
		"aud": "` + testAudience + `",
		"iss": "` + testIssuer + `",
		"iat": 1700175149,
		"exp": 1700175174
	}`

	result, err := Parse(createTokenFromJSON(payload))
	if err != nil {
		t.Fatalf("expected no error for an absent principal, got %v", err)
	}

	if result.AccountID != "" {
		t.Errorf("expected an empty accountId, got %q", result.AccountID)
	}

	if result.CloudID != testCloudID {
		t.Errorf("expected cloudId %q, got %q", testCloudID, result.CloudID)
	}
}

func TestParse_EmptyPrincipalYieldsEmptyAccountID(t *testing.T) {
	claims := validClaims()
	claims.Principal = ""

	result, err := Parse(createTestToken(claims))
	if err != nil {
		t.Fatalf("expected no error for an empty principal, got %v", err)
	}

	if result.AccountID != "" {
		t.Errorf("expected an empty accountId, got %q", result.AccountID)
	}

	if result.CloudID != testCloudID {
		t.Errorf("expected cloudId %q, got %q", testCloudID, result.CloudID)
	}
}

// TestParse_AbsentPrincipalFallsBackToAPIBaseURL pins the lifecycle path the
// gateway actually serves: a user-less FIT whose tenant is resolvable only from
// app.apiBaseUrl.
func TestParse_AbsentPrincipalFallsBackToAPIBaseURL(t *testing.T) {
	claims := validClaims()
	claims.Principal = ""
	claims.Context.CloudID = ""

	result, err := Parse(createTestToken(claims))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.AccountID != "" {
		t.Errorf("expected an empty accountId, got %q", result.AccountID)
	}

	if result.CloudID != testCloudID {
		t.Errorf("expected cloudId %q from apiBaseUrl, got %q", testCloudID, result.CloudID)
	}
}

// TestParse_MissingCloudIDRejectedWithoutPrincipal asserts the rejection is
// driven by the unresolvable cloudId and never cites the optional principal.
func TestParse_MissingCloudIDRejectedWithoutPrincipal(t *testing.T) {
	claims := validClaims()
	claims.Principal = ""
	claims.Context.CloudID = ""
	claims.App.APIBaseURL = ""

	_, err := Parse(createTestToken(claims))
	if err == nil {
		t.Fatal("expected error when both cloudId sources are absent, got nil")
	}

	if !errors.Is(err, ErrMissingClaims) {
		t.Errorf("expected ErrMissingClaims, got %v", err)
	}

	if strings.Contains(err.Error(), "principal") {
		t.Errorf("expected the message not to cite the principal claim, got %q", err.Error())
	}
}

func TestParse_NestedAppObjectDecoded(t *testing.T) {
	payload := `{
		"app": {
			"id": "ari:cloud:ecosystem::app/test-app-id",
			"installationId": "ari:cloud:ecosystem::installation/test",
			"apiBaseUrl": "https://api.atlassian.com/ex/jira/` + testCloudID + `",
			"appVersion": "2.0.0",
			"environment": {
				"id": "ari:cloud:ecosystem::environment/test-env-id"
			}
		},
		"context": {"cloudId": "` + testCloudID + `"},
		"principal": "` + testPrincipal + `",
		"aud": "` + testAudience + `",
		"iss": "` + testIssuer + `",
		"iat": 1700175149,
		"exp": 1700175174
	}`

	result, err := Parse(createTokenFromJSON(payload))
	if err != nil {
		t.Fatalf("expected no error parsing real FIT shape, got %v", err)
	}

	if result.Claims.App.APIBaseURL == "" {
		t.Error("expected nested app.apiBaseUrl to be decoded")
	}

	if result.Claims.App.AppVersion != "2.0.0" {
		t.Errorf("expected appVersion '2.0.0', got %q", result.Claims.App.AppVersion)
	}

	if result.Claims.App.Environment.ID == "" {
		t.Error("expected nested app.environment.id to be decoded")
	}

	if result.Claims.ExpiresAt != 1700175174 {
		t.Errorf("expected exp 1700175174, got %d", result.Claims.ExpiresAt)
	}
}

func TestParse_MalformedBase64(t *testing.T) {
	_, err := Parse("header.invalid-base64!@#.signature")
	if err == nil {
		t.Fatal("expected error for malformed base64, got nil")
	}

	if !errors.Is(err, ErrInvalidBase64) {
		t.Errorf("expected ErrInvalidBase64, got %v", err)
	}
}

func TestParse_InvalidTokenFormat(t *testing.T) {
	tests := []struct {
		name  string
		token string
	}{
		{"empty token", ""},
		{"single part", "onlyonepart"},
		{"two parts", "header.payload"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := Parse(tt.token)
			if err == nil {
				t.Fatal("expected error for invalid token format, got nil")
			}

			if !errors.Is(err, ErrInvalidToken) {
				t.Errorf("expected ErrInvalidToken, got %v", err)
			}
		})
	}
}

func TestExtractCloudID_VariousFormats(t *testing.T) {
	tests := []struct {
		name       string
		apiBaseURL string
		expected   string
		shouldErr  bool
	}{
		{
			name:       "standard jira format",
			apiBaseURL: "https://api.atlassian.com/ex/jira/" + testCloudID,
			expected:   testCloudID,
		},
		{
			name:       "confluence format",
			apiBaseURL: "https://api.atlassian.com/ex/confluence/xyz789",
			expected:   "xyz789",
		},
		{
			name:       "trailing slash",
			apiBaseURL: "https://api.atlassian.com/ex/jira/abc123-def-456/",
			expected:   "abc123-def-456",
		},
		{
			name:       "empty path",
			apiBaseURL: "",
			shouldErr:  true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := extractCloudID(tt.apiBaseURL)

			if tt.shouldErr {
				if err == nil {
					t.Fatal("expected error, got nil")
				}
				return
			}

			if err != nil {
				t.Fatalf("expected no error, got %v", err)
			}

			if result != tt.expected {
				t.Errorf("expected cloudId %q, got %q", tt.expected, result)
			}
		})
	}
}

func TestExtractAccountID(t *testing.T) {
	tests := []struct {
		name      string
		principal string
		expected  string
	}{
		{
			name:      "forge colon-prefixed format",
			principal: "655362:312d3308-8954-42b0-aa38-771a10c88656",
			expected:  "312d3308-8954-42b0-aa38-771a10c88656",
		},
		{
			name:      "full ARI format",
			principal: "ari:cloud:identity::user/5b10ac8d82e05b22cc7d4ef5",
			expected:  "5b10ac8d82e05b22cc7d4ef5",
		},
		{
			name:      "bare account id",
			principal: "5b10ac8d82e05b22cc7d4ef5",
			expected:  "5b10ac8d82e05b22cc7d4ef5",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if result := extractAccountID(tt.principal); result != tt.expected {
				t.Errorf("expected accountId %q, got %q", tt.expected, result)
			}
		})
	}
}

func TestParse_WithBearerPrefix(t *testing.T) {
	result, err := Parse("Bearer " + createTestToken(validClaims()))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.CloudID != testCloudID {
		t.Errorf("expected cloudId %q, got %q", testCloudID, result.CloudID)
	}
}

func TestParse_EnvironmentID_SingleSegmentForm(t *testing.T) {
	claims := validClaims()
	claims.App.Environment.ID = "ari:cloud:ecosystem::environment/single-env-uuid"

	result, err := Parse(createTestToken(claims))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.EnvironmentID != "single-env-uuid" {
		t.Errorf("expected environmentId %q, got %q", "single-env-uuid", result.EnvironmentID)
	}
}

func TestParse_EnvironmentID_TwoSegmentForm(t *testing.T) {
	claims := validClaims()
	claims.App.Environment.ID = "ari:cloud:ecosystem::environment/app-uuid/env-uuid"

	result, err := Parse(createTestToken(claims))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if result.EnvironmentID != "env-uuid" {
		t.Errorf("expected environmentId %q (trailing segment), got %q", "env-uuid", result.EnvironmentID)
	}
}

func TestParse_MissingEnvironmentID(t *testing.T) {
	claims := validClaims()
	claims.App.Environment.ID = ""

	_, err := Parse(createTestToken(claims))
	if err == nil {
		t.Fatal("expected error for missing app.environment.id, got nil")
	}

	if !errors.Is(err, ErrMissingClaims) {
		t.Errorf("expected ErrMissingClaims, got %v", err)
	}
}

func TestParse_MissingAppID(t *testing.T) {
	claims := validClaims()
	claims.App.ID = ""

	_, err := Parse(createTestToken(claims))
	if err == nil {
		t.Fatal("expected error for missing app.id, got nil")
	}

	if !errors.Is(err, ErrMissingClaims) {
		t.Errorf("expected ErrMissingClaims, got %v", err)
	}
}

func TestParse_EmptyTrailingSegment(t *testing.T) {
	claims := validClaims()
	claims.App.Environment.ID = "ari:cloud:ecosystem::environment/"

	_, err := Parse(createTestToken(claims))
	if err == nil {
		t.Fatal("expected error for empty trailing segment, got nil")
	}

	if !errors.Is(err, ErrMissingClaims) {
		t.Errorf("expected ErrMissingClaims, got %v", err)
	}
}

func createTestToken(claims Claims) string {
	claimsJSON, _ := json.Marshal(claims)
	return createTokenFromJSON(string(claimsJSON))
}

func createTokenFromJSON(payload string) string {
	header := map[string]string{"alg": "RS256", "typ": "JWT"}
	headerJSON, _ := json.Marshal(header)

	headerB64 := base64.RawURLEncoding.EncodeToString(headerJSON)
	claimsB64 := base64.RawURLEncoding.EncodeToString([]byte(payload))

	return headerB64 + "." + claimsB64 + ".fake-signature"
}
