package jira

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const (
	backoffUnit       = 100 * time.Millisecond
	defaultMaxRetries = 3
	defaultTimeout    = 2 * time.Second
)

// unrecognizedPermissionLogFormat reports a 400 naming a permission Jira does
// not recognise. It is distinct from an ordinary denial so a malformed
// permission identifier is diagnosable from logs alone.
const unrecognizedPermissionLogFormat = "Jira rejected an unrecognized permission: %s"

// Client calls the Jira Cloud REST v3 permission check endpoint.
//
// wait defers the next attempt after a retryable failure. It is a field so
// tests can observe the requested interval without sleeping for it.
//
// logf records client diagnostics. It is a field so tests can assert that a
// rejection is reported distinctly without redirecting the global logger.
type Client struct {
	baseURL    string
	httpClient *http.Client
	maxRetries int
	timeout    time.Duration
	wait       func(ctx context.Context, delay time.Duration) error
	logf       func(format string, args ...any)
}

func NewClient(baseURL string) *Client {
	return &Client{
		baseURL:    baseURL,
		httpClient: &http.Client{},
		maxRetries: defaultMaxRetries,
		timeout:    defaultTimeout,
		wait:       waitFor,
		logf:       log.Printf,
	}
}

// CheckPermissions posts the permission check and retries retryable failures.
//
// The interval between attempts is the greater of the quadratic backoff and any
// Retry-After interval advertised by Jira. It is bounded by the caller's
// context, not by the shorter per-attempt timeout, so an advertised interval is
// honoured in full or the retry is abandoned rather than being truncated.
func (c *Client) CheckPermissions(ctx context.Context, cloudID, systemToken string, req *BulkPermissionsRequestBean) ([]string, error) {
	url := fmt.Sprintf("%s/ex/jira/%s/rest/api/3/permissions/check", c.baseURL, cloudID)

	var lastErr error
	for attempt := 0; attempt <= c.maxRetries; attempt++ {
		permissions, err := c.attempt(ctx, url, systemToken, req)
		if err == nil {
			return permissions, nil
		}

		lastErr = err

		if !isRetryable(err) || attempt == c.maxRetries {
			break
		}

		if waitErr := c.wait(ctx, retryDelay(attempt+1, retryAfterOf(err))); waitErr != nil {
			return nil, waitErr
		}
	}

	return nil, fmt.Errorf("jira api check permissions failed after %d retries: %w", c.maxRetries, lastErr)
}

func (c *Client) attempt(ctx context.Context, url, systemToken string, req *BulkPermissionsRequestBean) ([]string, error) {
	reqCtx, cancel := context.WithTimeout(ctx, c.timeout)
	defer cancel()

	return c.doRequest(reqCtx, url, systemToken, req)
}

func waitFor(ctx context.Context, delay time.Duration) error {
	if delay <= 0 {
		return nil
	}

	timer := time.NewTimer(delay)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

func retryDelay(attempt int, retryAfter time.Duration) time.Duration {
	backoff := time.Duration(attempt*attempt) * backoffUnit
	if retryAfter > backoff {
		return retryAfter
	}

	return backoff
}

func retryAfterOf(err error) time.Duration {
	var retryableErr *RetryableError
	if errors.As(err, &retryableErr) {
		return retryableErr.RetryAfter
	}

	return 0
}

func (c *Client) doRequest(ctx context.Context, url, systemToken string, req *BulkPermissionsRequestBean) ([]string, error) {
	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Authorization", "Bearer "+systemToken)

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, &RetryableError{Err: err}
	}
	defer func() {
		if closeErr := resp.Body.Close(); closeErr != nil {
			c.logf("Failed to close jira response body: %v", closeErr)
		}
	}()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode == http.StatusTooManyRequests {
		return nil, &RetryableError{
			Err:        fmt.Errorf("rate limit exceeded (429)"),
			RetryAfter: parseRetryAfter(resp.Header.Get("Retry-After")),
		}
	}

	if resp.StatusCode >= 500 {
		return nil, &RetryableError{Err: fmt.Errorf("server error (%d): %s", resp.StatusCode, string(respBody))}
	}

	if resp.StatusCode == http.StatusBadRequest {
		if containsUnrecognizedPermission(respBody) {
			c.logf(unrecognizedPermissionLogFormat, string(respBody))
		}
		var errResp ErrorResponse
		if json.Unmarshal(respBody, &errResp) == nil && len(errResp.ErrorMessages) > 0 {
			return nil, fmt.Errorf("jira api error (%d): %s", resp.StatusCode, errResp.ErrorMessages[0])
		}
		return nil, fmt.Errorf("jira api error (%d): %s", resp.StatusCode, string(respBody))
	}

	if resp.StatusCode != http.StatusOK {
		var errResp ErrorResponse
		if json.Unmarshal(respBody, &errResp) == nil && len(errResp.ErrorMessages) > 0 {
			return nil, fmt.Errorf("jira api error (%d): %s", resp.StatusCode, errResp.ErrorMessages[0])
		}
		return nil, fmt.Errorf("jira api error (%d): %s", resp.StatusCode, string(respBody))
	}

	var checkResp BulkPermissionGrants
	if err := json.Unmarshal(respBody, &checkResp); err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	if checkResp.GlobalPermissions == nil || checkResp.ProjectPermissions == nil {
		return nil, fmt.Errorf("malformed jira response: missing required members")
	}

	return extractGrantedPermissions(&checkResp), nil
}

func parseRetryAfter(header string) time.Duration {
	if header == "" {
		return 0
	}

	if seconds, err := strconv.Atoi(header); err == nil {
		return time.Duration(seconds) * time.Second
	}

	return 0
}

func containsUnrecognizedPermission(body []byte) bool {
	bodyStr := string(body)
	return strings.Contains(bodyStr, "Unrecognized permission") ||
		strings.Contains(bodyStr, "unrecognized permission")
}

// extractGrantedPermissions collects every identifier the response reports as
// held. The result is always non-nil so a caller serialising it produces an
// empty JSON array rather than null.
func extractGrantedPermissions(resp *BulkPermissionGrants) []string {
	granted := []string{}

	if resp.GlobalPermissions != nil {
		granted = append(granted, *resp.GlobalPermissions...)
	}

	if resp.ProjectPermissions != nil {
		for _, grant := range *resp.ProjectPermissions {
			granted = append(granted, grant.Permission)
		}
	}

	return granted
}

func isRetryable(err error) bool {
	var retryableErr *RetryableError
	return err != nil && (errors.As(err, &retryableErr) || errors.Is(err, context.DeadlineExceeded))
}

// RetryableError marks a failure worth retrying. RetryAfter carries the
// interval Jira advertised on a 429, and is zero when none was supplied.
type RetryableError struct {
	Err        error
	RetryAfter time.Duration
}

func (e *RetryableError) Error() string {
	return e.Err.Error()
}

func (e *RetryableError) Unwrap() error {
	return e.Err
}
