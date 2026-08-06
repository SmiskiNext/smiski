package jira

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"sort"
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

func writeRaw(t *testing.T, w http.ResponseWriter, payload string) {
	t.Helper()

	if _, err := w.Write([]byte(payload)); err != nil {
		t.Errorf("failed to write response: %v", err)
	}
}

func buildPermissionARI(key string) string {
	return "ari:cloud:ecosystem::extension/app-uuid/env-uuid/static/" + key
}

func newTestClient(t *testing.T, baseURL string) *Client {
	t.Helper()

	client := NewClient(baseURL)
	client.wait = func(context.Context, time.Duration) error { return nil }

	return client
}

// logRecorder captures client diagnostics through the injectable logf hook so
// assertions never depend on the process-wide standard logger.
type logRecorder struct {
	entries []string
}

func (r *logRecorder) record(format string, args ...any) {
	r.entries = append(r.entries, fmt.Sprintf(format, args...))
}

func (r *logRecorder) matching(substring string) []string {
	matches := []string{}

	for _, entry := range r.entries {
		if strings.Contains(entry, substring) {
			matches = append(matches, entry)
		}
	}

	return matches
}

func newRecordingClient(t *testing.T, baseURL string) (*Client, *logRecorder) {
	t.Helper()

	recorder := &logRecorder{}
	client := newTestClient(t, baseURL)
	client.logf = recorder.record

	return client, recorder
}

func issueScopedRequest(permissions ...string) *BulkPermissionsRequestBean {
	return &BulkPermissionsRequestBean{
		AccountID: "test-account",
		ProjectPermissions: []BulkProjectPermissions{
			{Permissions: permissions, Issues: []int64{10001}},
		},
	}
}

func TestCheckPermissions_Success(t *testing.T) {
	viewARI := buildPermissionARI("view-meeting")
	editARI := buildPermissionARI("edit-meeting")

	response := `{
		"globalPermissions": [],
		"projectPermissions": [
			{"permission": "` + viewARI + `", "issues": [10001], "projects": []},
			{"permission": "` + editARI + `", "issues": [10001], "projects": []}
		]
	}`

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
		writeRaw(t, w, response)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token", issueScopedRequest(viewARI, editARI))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(permissions) != 2 {
		t.Fatalf("expected 2 permissions, got %d (%v)", len(permissions), permissions)
	}

	if permissions[0] != viewARI || permissions[1] != editARI {
		t.Errorf("unexpected permissions: %v", permissions)
	}
}

func TestCheckPermissions_PartialPermissions(t *testing.T) {
	viewARI := buildPermissionARI("view-meeting")

	response := `{
		"globalPermissions": [],
		"projectPermissions": [
			{"permission": "` + viewARI + `", "issues": [10001], "projects": []}
		]
	}`

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		writeRaw(t, w, response)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(viewARI, buildPermissionARI("edit-meeting")))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(permissions) != 1 {
		t.Fatalf("expected 1 permission, got %d (%v)", len(permissions), permissions)
	}

	if permissions[0] != viewARI {
		t.Errorf("expected '%s', got '%s'", viewARI, permissions[0])
	}
}

func TestCheckPermissions_NoPermissions(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		writeRaw(t, w, `{"globalPermissions":[],"projectPermissions":[]}`)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting")))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(permissions) != 0 {
		t.Errorf("expected 0 permissions, got %d", len(permissions))
	}

	if permissions == nil {
		t.Error("expected an empty non-nil slice so the result serialises as [] rather than null")
	}
}

func TestCheckPermissions_GlobalPermissionsGranted(t *testing.T) {
	globalARI := buildPermissionARI("view-meeting")

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		writeRaw(t, w, `{"globalPermissions":["`+globalARI+`"],"projectPermissions":[]}`)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(globalARI))
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(permissions) != 1 || permissions[0] != globalARI {
		t.Errorf("expected [%s], got %v", globalARI, permissions)
	}
}

func TestCheckPermissions_RequestBodyMatchesSchema(t *testing.T) {
	var captured []byte

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(r.Body)
		if err != nil {
			t.Errorf("failed to read request body: %v", err)
		}
		captured = body

		w.WriteHeader(http.StatusOK)
		writeRaw(t, w, `{"globalPermissions":[],"projectPermissions":[]}`)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	if _, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting"))); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	var body map[string]any
	if err := json.Unmarshal(captured, &body); err != nil {
		t.Fatalf("failed to unmarshal captured request body: %v", err)
	}

	assertExactKeys(t, "request body", body, "accountId", "projectPermissions")

	if body["accountId"] != "test-account" {
		t.Errorf("expected accountId 'test-account', got %v", body["accountId"])
	}

	entry := firstProjectPermission(t, body)
	assertExactKeys(t, "projectPermissions[0]", entry, "permissions", "issues")
	assertJSONNumbers(t, "issues", entry["issues"], 10001)
}

func TestCheckPermissions_ProjectScopedRequestBodyMatchesSchema(t *testing.T) {
	var captured []byte

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(r.Body)
		if err != nil {
			t.Errorf("failed to read request body: %v", err)
		}
		captured = body

		w.WriteHeader(http.StatusOK)
		writeRaw(t, w, `{"globalPermissions":[],"projectPermissions":[]}`)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	req := &BulkPermissionsRequestBean{
		AccountID: "test-account",
		ProjectPermissions: []BulkProjectPermissions{
			{Permissions: []string{buildPermissionARI("view-meeting")}, Projects: []int64{10002}},
		},
	}

	if _, err := client.CheckPermissions(context.Background(), "", "test-token", req); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	var body map[string]any
	if err := json.Unmarshal(captured, &body); err != nil {
		t.Fatalf("failed to unmarshal captured request body: %v", err)
	}

	assertExactKeys(t, "request body", body, "accountId", "projectPermissions")

	entry := firstProjectPermission(t, body)
	assertExactKeys(t, "projectPermissions[0]", entry, "permissions", "projects")
	assertJSONNumbers(t, "projects", entry["projects"], 10002)
}

func firstProjectPermission(t *testing.T, body map[string]any) map[string]any {
	t.Helper()

	entries, ok := body["projectPermissions"].([]any)
	if !ok {
		t.Fatalf("expected projectPermissions to be a JSON array, got %T", body["projectPermissions"])
	}

	if len(entries) != 1 {
		t.Fatalf("expected exactly 1 projectPermissions entry, got %d", len(entries))
	}

	entry, ok := entries[0].(map[string]any)
	if !ok {
		t.Fatalf("expected projectPermissions[0] to be a JSON object, got %T", entries[0])
	}

	return entry
}

func assertExactKeys(t *testing.T, label string, object map[string]any, expected ...string) {
	t.Helper()

	allowed := make(map[string]bool, len(expected))
	for _, key := range expected {
		allowed[key] = true
		if _, present := object[key]; !present {
			t.Errorf("%s: expected member %q to be present, got keys %v", label, key, sortedKeys(object))
		}
	}

	for key := range object {
		if !allowed[key] {
			t.Errorf("%s: unexpected member %q; the schema forbids additional properties (keys: %v)",
				label, key, sortedKeys(object))
		}
	}
}

func assertJSONNumbers(t *testing.T, label string, value any, expected ...float64) {
	t.Helper()

	values, ok := value.([]any)
	if !ok {
		t.Fatalf("%s: expected a JSON array, got %T", label, value)
	}

	if len(values) != len(expected) {
		t.Fatalf("%s: expected %d values, got %d", label, len(expected), len(values))
	}

	for i, entry := range values {
		number, ok := entry.(float64)
		if !ok {
			t.Errorf("%s[%d]: expected a JSON number, got %T (%v)", label, i, entry, entry)
			continue
		}

		if number != expected[i] {
			t.Errorf("%s[%d]: expected %v, got %v", label, i, expected[i], number)
		}
	}
}

func sortedKeys(object map[string]any) []string {
	keys := make([]string, 0, len(object))
	for key := range object {
		keys = append(keys, key)
	}
	sort.Strings(keys)

	return keys
}

func TestCheckPermissions_MalformedResponse_MissingRequiredMember(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		writeRaw(t, w, `{"globalPermissions":[]}`)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	_, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting")))
	if err == nil {
		t.Fatal("expected error for malformed response, got nil")
	}

	if !strings.Contains(err.Error(), "missing required members") {
		t.Errorf("expected 'missing required members' error, got: %v", err)
	}
}

func TestCheckPermissions_ResponseWithUnknownFields(t *testing.T) {
	viewARI := buildPermissionARI("view-meeting")

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		writeRaw(t, w, `{
			"globalPermissions": [],
			"projectPermissions": [{"permission":"`+viewARI+`","issues":[10001]}],
			"unknownField": "ignored"
		}`)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(viewARI))
	if err != nil {
		t.Fatalf("expected no error for unknown fields, got %v", err)
	}

	if len(permissions) != 1 || permissions[0] != viewARI {
		t.Errorf("expected [%s], got %v", viewARI, permissions)
	}
}

func TestCheckPermissions_Timeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(3 * time.Second)
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)
	client.timeout = 100 * time.Millisecond

	_, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting")))
	if err == nil {
		t.Fatal("expected timeout error, got nil")
	}
}

func TestCheckPermissions_RateLimitRetry(t *testing.T) {
	attempts := 0
	viewARI := buildPermissionARI("view-meeting")

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts < 3 {
			w.WriteHeader(http.StatusTooManyRequests)
			return
		}

		w.WriteHeader(http.StatusOK)
		writeRaw(t, w, `{"globalPermissions":[],"projectPermissions":[{"permission":"`+viewARI+`","issues":[10001]}]}`)
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	permissions, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(viewARI))
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

func TestCheckPermissions_RetryAfterOverridesBackoff(t *testing.T) {
	var delays []time.Duration

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Retry-After", "3")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.wait = func(_ context.Context, delay time.Duration) error {
		delays = append(delays, delay)
		return nil
	}

	_, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting")))
	if err == nil {
		t.Fatal("expected error after exhausting retries, got nil")
	}

	if len(delays) != defaultMaxRetries {
		t.Fatalf("expected %d deferred retries, got %d (%v)", defaultMaxRetries, len(delays), delays)
	}

	for i, delay := range delays {
		if delay < 3*time.Second {
			t.Errorf("retry %d: expected the advertised 3s Retry-After interval to govern, got %v", i+1, delay)
		}
	}
}

func TestCheckPermissions_RetryAfterNotTruncatedByPerAttemptTimeout(t *testing.T) {
	var delays []time.Duration

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Retry-After", "60")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.wait = func(_ context.Context, delay time.Duration) error {
		delays = append(delays, delay)
		return nil
	}

	if _, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting"))); err == nil {
		t.Fatal("expected error after exhausting retries, got nil")
	}

	if len(delays) == 0 {
		t.Fatal("expected at least one deferred retry")
	}

	if delays[0] < 60*time.Second {
		t.Errorf("expected the full 60s Retry-After interval, not one truncated by the %v per-attempt timeout, got %v",
			client.timeout, delays[0])
	}
}

func TestCheckPermissions_RetryAfterExceedingDeadlineAbandonsRetry(t *testing.T) {
	attempts := 0

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.Header().Set("Retry-After", "60")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := NewClient(server.URL)

	ctx, cancel := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancel()

	start := time.Now()
	_, err := client.CheckPermissions(ctx, "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting")))
	elapsed := time.Since(start)

	if err == nil {
		t.Fatal("expected an error once the deadline passes, got nil")
	}

	if elapsed >= 60*time.Second {
		t.Errorf("expected the retry to be abandoned at the deadline, waited %v", elapsed)
	}

	if attempts != 1 {
		t.Errorf("expected the retry to be abandoned after the first attempt, got %d attempts", attempts)
	}
}

type callerContextKey struct{}

func TestCheckPermissions_WaitBoundedByCallerContextNotPerAttemptTimeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Retry-After", "60")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := NewClient(server.URL)

	marked := context.WithValue(context.Background(), callerContextKey{}, "caller")
	ctx, cancel := context.WithTimeout(marked, time.Hour)
	defer cancel()

	callerDeadline, hasCallerDeadline := ctx.Deadline()
	if !hasCallerDeadline {
		t.Fatal("expected the caller context to carry a deadline")
	}

	type observedContext struct {
		marker      any
		deadline    time.Time
		hasDeadline bool
	}

	var observed []observedContext
	client.wait = func(waitCtx context.Context, _ time.Duration) error {
		deadline, hasDeadline := waitCtx.Deadline()
		observed = append(observed, observedContext{
			marker:      waitCtx.Value(callerContextKey{}),
			deadline:    deadline,
			hasDeadline: hasDeadline,
		})

		return nil
	}

	if _, err := client.CheckPermissions(ctx, "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting"))); err == nil {
		t.Fatal("expected error after exhausting retries, got nil")
	}

	if len(observed) == 0 {
		t.Fatal("expected at least one deferred retry")
	}

	for i, got := range observed {
		if got.marker != "caller" {
			t.Errorf("retry %d: expected the wait to receive a context derived from the caller's, got marker %v",
				i+1, got.marker)
		}

		if !got.hasDeadline {
			t.Errorf("retry %d: expected the caller's deadline to reach the wait", i+1)
			continue
		}

		if !got.deadline.Equal(callerDeadline) {
			t.Errorf("retry %d: expected the wait bounded by the caller's deadline %v, got %v; a %v per-attempt bound would silently truncate the advertised interval",
				i+1, callerDeadline, got.deadline, client.timeout)
		}
	}
}

func TestCheckPermissions_RateLimitWithoutRetryAfterUsesBackoff(t *testing.T) {
	var delays []time.Duration

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := NewClient(server.URL)
	client.wait = func(_ context.Context, delay time.Duration) error {
		delays = append(delays, delay)
		return nil
	}

	if _, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting"))); err == nil {
		t.Fatal("expected error after exhausting retries, got nil")
	}

	expected := []time.Duration{backoffUnit, 4 * backoffUnit, 9 * backoffUnit}
	if len(delays) != len(expected) {
		t.Fatalf("expected %d deferred retries, got %d (%v)", len(expected), len(delays), delays)
	}

	for i, delay := range delays {
		if delay != expected[i] {
			t.Errorf("retry %d: expected the quadratic backoff %v, got %v", i+1, expected[i], delay)
		}
	}
}

func TestParseRetryAfter(t *testing.T) {
	tests := []struct {
		name     string
		header   string
		expected time.Duration
	}{
		{name: "absent", header: "", expected: 0},
		{name: "seconds", header: "60", expected: 60 * time.Second},
		{name: "non-numeric", header: "Wed, 21 Oct 2015 07:28:00 GMT", expected: 0},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := parseRetryAfter(tt.header); got != tt.expected {
				t.Errorf("expected %v, got %v", tt.expected, got)
			}
		})
	}
}

func TestCheckPermissions_ServerError(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		w.WriteHeader(http.StatusInternalServerError)
		writeRaw(t, w, "Internal Server Error")
	}))
	defer server.Close()

	client := newTestClient(t, server.URL)

	_, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting")))
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

	client := newTestClient(t, server.URL)

	_, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting")))
	if err == nil {
		t.Fatal("expected error for bad request, got nil")
	}

	if attempts != 1 {
		t.Errorf("expected 1 attempt (no retry for 400), got %d", attempts)
	}
}

func TestExtractGrantedPermissions(t *testing.T) {
	globalPerm := "ari:cloud:ecosystem::extension/app/env/static/global-perm"
	viewARI := buildPermissionARI("view-meeting")
	editARI := buildPermissionARI("edit-meeting")
	deleteARI := buildPermissionARI("delete-meeting")

	resp := &BulkPermissionGrants{
		GlobalPermissions: &[]string{globalPerm},
		ProjectPermissions: &[]BulkProjectPermissionGrants{
			{Permission: viewARI, Issues: []int64{10001}},
			{Permission: deleteARI, Projects: []int64{10002}},
		},
	}

	granted := extractGrantedPermissions(resp)

	if len(granted) != 3 {
		t.Errorf("expected 3 granted permissions, got %d", len(granted))
	}

	expected := map[string]bool{
		globalPerm: true,
		viewARI:    true,
		deleteARI:  true,
	}

	for _, perm := range granted {
		if !expected[perm] {
			t.Errorf("unexpected permission: %s", perm)
		}
	}

	for _, perm := range granted {
		if perm == editARI {
			t.Errorf("edit-meeting should not be granted, but found: %s", perm)
		}
	}
}

func TestExtractGrantedPermissions_EmptyResponseIsNonNil(t *testing.T) {
	resp := &BulkPermissionGrants{
		GlobalPermissions:  &[]string{},
		ProjectPermissions: &[]BulkProjectPermissionGrants{},
	}

	granted := extractGrantedPermissions(resp)

	if granted == nil {
		t.Fatal("expected a non-nil empty slice so the result serialises as [] rather than null")
	}

	encoded, err := json.Marshal(granted)
	if err != nil {
		t.Fatalf("failed to marshal granted permissions: %v", err)
	}

	if string(encoded) != "[]" {
		t.Errorf("expected the empty result to serialise as [], got %s", encoded)
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
		writeRaw(t, w, "not json")
	}))
	defer server.Close()

	client := NewClient(server.URL)

	_, err := client.doRequest(context.Background(), server.URL, "test-token", &BulkPermissionsRequestBean{})
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
		writeRaw(t, w, "plain failure")
	}))
	defer server.Close()

	client := NewClient(server.URL)

	_, err := client.doRequest(context.Background(), server.URL, "test-token", &BulkPermissionsRequestBean{})
	if err == nil {
		t.Fatal("expected error for bad request, got nil")
	}

	if !strings.Contains(err.Error(), "plain failure") {
		t.Errorf("expected the raw body in the error, got '%s'", err.Error())
	}
}

func TestDoRequest_RateLimitCarriesRetryAfter(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Retry-After", "45")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer server.Close()

	client := NewClient(server.URL)

	_, err := client.doRequest(context.Background(), server.URL, "test-token", &BulkPermissionsRequestBean{})
	if err == nil {
		t.Fatal("expected a rate limit error, got nil")
	}

	var retryableErr *RetryableError
	if !errors.As(err, &retryableErr) {
		t.Fatalf("expected a retryable error, got %v", err)
	}

	if retryableErr.RetryAfter != 45*time.Second {
		t.Errorf("expected the advertised interval to be carried to the retry loop, got %v", retryableErr.RetryAfter)
	}
}

func TestDoRequest_InvalidURLFailsRequestCreation(t *testing.T) {
	client := NewClient("http://example.invalid")

	_, err := client.doRequest(context.Background(), "://bad-url", "test-token", &BulkPermissionsRequestBean{})
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

	_, err := client.doRequest(context.Background(), url, "test-token", &BulkPermissionsRequestBean{})
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

	_, err := client.CheckPermissions(ctx, "", "test-token", &BulkPermissionsRequestBean{})
	if err == nil {
		t.Fatal("expected error for a cancelled context, got nil")
	}
}

func distinctRejectionMarker() string {
	return strings.TrimSuffix(unrecognizedPermissionLogFormat, "%s")
}

func TestCheckPermissions_UnrecognizedPermissionLoggedDistinctly(t *testing.T) {
	rejectedARI := buildPermissionARI("view-meeting")

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		encodeJSON(t, w, ErrorResponse{
			ErrorMessages: []string{"Unrecognized permission key " + rejectedARI},
		})
	}))
	defer server.Close()

	client, recorder := newRecordingClient(t, server.URL)

	_, err := client.CheckPermissions(context.Background(), "", "test-token", issueScopedRequest(rejectedARI))
	if err == nil {
		t.Fatal("expected error for unrecognized permission, got nil")
	}

	matches := recorder.matching(distinctRejectionMarker())
	if len(matches) != 1 {
		t.Fatalf("expected exactly one distinct rejection log, got %d (%v)", len(matches), recorder.entries)
	}

	if !strings.Contains(matches[0], rejectedARI) {
		t.Errorf("expected the rejected identifier %q in the log so a malformed ARI is diagnosable, got %q",
			rejectedARI, matches[0])
	}
}

func TestCheckPermissions_OrdinaryBadRequestNotLoggedAsUnrecognizedPermission(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		encodeJSON(t, w, ErrorResponse{
			ErrorMessages: []string{"The issue does not exist or you do not have permission to see it."},
		})
	}))
	defer server.Close()

	client, recorder := newRecordingClient(t, server.URL)

	_, err := client.CheckPermissions(context.Background(), "", "test-token",
		issueScopedRequest(buildPermissionARI("view-meeting")))
	if err == nil {
		t.Fatal("expected error for bad request, got nil")
	}

	if matches := recorder.matching(distinctRejectionMarker()); len(matches) != 0 {
		t.Errorf("expected an ordinary denial to be reported distinctly from a rejected identifier, got %v", matches)
	}
}
