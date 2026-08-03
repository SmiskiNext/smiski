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
	"time"
)

type Client struct {
	baseURL    string
	httpClient *http.Client
	maxRetries int
	timeout    time.Duration
}

func NewClient(baseURL string) *Client {
	return &Client{
		baseURL:    baseURL,
		httpClient: &http.Client{},
		maxRetries: 3,
		timeout:    2 * time.Second,
	}
}

func (c *Client) CheckPermissions(ctx context.Context, cloudID, systemToken string, req *PermissionsCheckRequest) ([]string, error) {
	url := fmt.Sprintf("%s/ex/jira/%s/rest/api/3/permissions/check", c.baseURL, cloudID)

	var lastErr error
	for attempt := 0; attempt <= c.maxRetries; attempt++ {
		if attempt > 0 {
			backoff := time.Duration(attempt*attempt) * 100 * time.Millisecond
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-time.After(backoff):
			}
		}

		reqCtx, cancel := context.WithTimeout(ctx, c.timeout)
		defer cancel()

		permissions, err := c.doRequest(reqCtx, url, systemToken, req)
		if err == nil {
			return permissions, nil
		}

		lastErr = err

		if !isRetryable(err) {
			break
		}
	}

	return nil, fmt.Errorf("jira api check permissions failed after %d retries: %w", c.maxRetries, lastErr)
}

func (c *Client) doRequest(ctx context.Context, url, systemToken string, req *PermissionsCheckRequest) ([]string, error) {
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
			log.Printf("Failed to close jira response body: %v", closeErr)
		}
	}()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode == http.StatusTooManyRequests {
		return nil, &RetryableError{Err: fmt.Errorf("rate limit exceeded (429)")}
	}

	if resp.StatusCode >= 500 {
		return nil, &RetryableError{Err: fmt.Errorf("server error (%d): %s", resp.StatusCode, string(respBody))}
	}

	if resp.StatusCode != http.StatusOK {
		var errResp ErrorResponse
		if json.Unmarshal(respBody, &errResp) == nil && len(errResp.ErrorMessages) > 0 {
			return nil, fmt.Errorf("jira api error (%d): %s", resp.StatusCode, errResp.ErrorMessages[0])
		}
		return nil, fmt.Errorf("jira api error (%d): %s", resp.StatusCode, string(respBody))
	}

	var checkResp PermissionsCheckResponse
	if err := json.Unmarshal(respBody, &checkResp); err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	return extractGrantedPermissions(&checkResp), nil
}

func extractGrantedPermissions(resp *PermissionsCheckResponse) []string {
	var granted []string

	for _, perm := range resp.GlobalPermissions {
		if perm.HasPermission {
			granted = append(granted, perm.Key)
		}
	}

	for _, perm := range resp.ProjectPermissions {
		if perm.HasPermission {
			granted = append(granted, perm.Key)
		}
	}

	return granted
}

func isRetryable(err error) bool {
	var retryableErr *RetryableError
	return err != nil && (errors.As(err, &retryableErr) || errors.Is(err, context.DeadlineExceeded))
}

type RetryableError struct {
	Err error
}

func (e *RetryableError) Error() string {
	return e.Err.Error()
}

func (e *RetryableError) Unwrap() error {
	return e.Err
}
