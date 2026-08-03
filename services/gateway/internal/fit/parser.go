package fit

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
)

// App holds the Forge application and installation context nested under the
// FIT "app" claim.
type App struct {
	ID             string `json:"id"`
	InstallationID string `json:"installationId"`
	APIBaseURL     string `json:"apiBaseUrl"`
	AppVersion     string `json:"appVersion"`
}

// Context holds the product context nested under the FIT "context" claim.
// CloudID is the authoritative Jira cloud site identifier used as tenant id.
type Context struct {
	CloudID string `json:"cloudId"`
}

// Claims mirrors the Forge Invocation Token payload documented at
// https://developer.atlassian.com/platform/forge/remote/essentials/.
type Claims struct {
	App       App     `json:"app"`
	Context   Context `json:"context"`
	Principal string  `json:"principal"`
	Issuer    string  `json:"iss"`
	Audience  string  `json:"aud"`
	IssuedAt  int64   `json:"iat"`
	ExpiresAt int64   `json:"exp"`
}

// ParsedFIT carries the identity derived from a Forge Invocation Token.
type ParsedFIT struct {
	CloudID   string
	AccountID string
	Claims    Claims
}

var (
	ErrInvalidToken      = errors.New("invalid token format")
	ErrMissingClaims     = errors.New("missing required claims")
	ErrInvalidBase64     = errors.New("invalid base64 encoding")
	ErrCloudIDExtraction = errors.New("failed to extract cloudId from apiBaseUrl")
)

// Parse decodes a Forge Invocation Token payload and derives tenant and account
// identity. Signature verification happens upstream in the Envoy jwt_authn
// filter, so this only decodes the payload segment.
func Parse(token string) (*ParsedFIT, error) {
	if token == "" {
		return nil, ErrInvalidToken
	}

	token = strings.TrimPrefix(token, "Bearer ")
	token = strings.TrimSpace(token)

	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, ErrInvalidToken
	}

	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrInvalidBase64, err)
	}

	var claims Claims
	if err := json.Unmarshal(payload, &claims); err != nil {
		return nil, fmt.Errorf("failed to unmarshal claims: %w", err)
	}

	if claims.Principal == "" {
		return nil, fmt.Errorf("%w: principal claim missing", ErrMissingClaims)
	}

	cloudID, err := resolveCloudID(claims)
	if err != nil {
		return nil, err
	}

	return &ParsedFIT{
		CloudID:   cloudID,
		AccountID: extractAccountID(claims.Principal),
		Claims:    claims,
	}, nil
}

// resolveCloudID prefers the explicit context.cloudId claim and falls back to
// parsing the trailing path segment of app.apiBaseUrl.
func resolveCloudID(claims Claims) (string, error) {
	if claims.Context.CloudID != "" {
		return claims.Context.CloudID, nil
	}

	if claims.App.APIBaseURL == "" {
		return "", fmt.Errorf(
			"%w: neither context.cloudId nor app.apiBaseUrl present", ErrMissingClaims)
	}

	return extractCloudID(claims.App.APIBaseURL)
}

// extractCloudID reads the cloud identifier from an apiBaseUrl shaped like
// https://api.atlassian.com/ex/jira/{cloudId}.
func extractCloudID(apiBaseURL string) (string, error) {
	parts := strings.Split(strings.TrimSuffix(apiBaseURL, "/"), "/")
	if len(parts) < 1 {
		return "", ErrCloudIDExtraction
	}

	cloudID := parts[len(parts)-1]
	if cloudID == "" {
		return "", ErrCloudIDExtraction
	}

	return cloudID, nil
}

// extractAccountID normalises the principal claim into a bare Atlassian account
// id. Forge emits either "655362:<uuid>" or an ARI such as
// "ari:cloud:identity::user/<accountId>".
func extractAccountID(principal string) string {
	if idx := strings.LastIndex(principal, "/"); idx != -1 {
		return principal[idx+1:]
	}

	if idx := strings.LastIndex(principal, ":"); idx != -1 {
		return principal[idx+1:]
	}

	return principal
}
