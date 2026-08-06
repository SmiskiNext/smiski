package authz

import (
	"context"
	"errors"
	"fmt"
	"log"
	"strconv"
	"strings"

	"github.com/smiskinext/gateway/internal/fit"
	"github.com/smiskinext/gateway/internal/jira"
)

const (
	viewMeetingKey = "view-meeting"
	editMeetingKey = "edit-meeting"
)

// ErrMissingSystemToken reports an absent x-forge-oauth-system header. It marks
// a deployment fault rather than a permission denial, so it is reported to the
// caller instead of entering the stale-cache fallback that absorbs Jira API
// failures.
var ErrMissingSystemToken = errors.New("missing system token")

type JiraClient interface {
	CheckPermissions(ctx context.Context, cloudID, systemToken string, req *jira.BulkPermissionsRequestBean) ([]string, error)
}

type Cache interface {
	Get(ctx context.Context, key string) ([]string, error)
	Set(ctx context.Context, key string, permissions []string) error
	GetStale(ctx context.Context, key string) ([]string, error)
	GenerateKey(cloudID, accountID, context string) string
}

type AuthzService struct {
	jiraClient JiraClient
	cache      Cache
}

func NewAuthzService(jiraClient JiraClient, cache Cache) *AuthzService {
	return &AuthzService{
		jiraClient: jiraClient,
		cache:      cache,
	}
}

type AuthzRequest struct {
	FITToken    string
	SystemToken string
	IssueID     string
	ProjectID   string
}

type AuthzResult struct {
	CloudID     string
	AccountID   string
	Permissions []string
}

// contextScope identifies the Jira resource a permission check is evaluated
// against. Presence is tracked separately from the numeric value so a
// legitimate identifier of "0" is not mistaken for an absent header.
type contextScope struct {
	issuePresent   bool
	issueID        int64
	projectPresent bool
	projectID      int64
}

func (s *AuthzService) Authorize(ctx context.Context, req *AuthzRequest) (*AuthzResult, error) {
	if req.FITToken == "" {
		return nil, fmt.Errorf("missing FIT token")
	}

	parsedFIT, err := fit.Parse(req.FITToken)
	if err != nil {
		return nil, fmt.Errorf("failed to parse FIT: %w", err)
	}

	result := &AuthzResult{
		CloudID:     parsedFIT.CloudID,
		AccountID:   parsedFIT.AccountID,
		Permissions: []string{},
	}

	if req.IssueID == "" && req.ProjectID == "" {
		log.Printf("No context headers present, skipping permission check for accountId=%s", parsedFIT.AccountID)
		return result, nil
	}

	scope, err := parseContextScope(req.IssueID, req.ProjectID)
	if err != nil {
		log.Printf("Failed to parse context IDs: %v", err)
		return nil, fmt.Errorf("invalid context identifier: %w", err)
	}

	cacheKey := s.generateCacheKey(parsedFIT.CloudID, parsedFIT.AccountID, req.IssueID, req.ProjectID)

	cachedPerms, err := s.cache.Get(ctx, cacheKey)
	if err == nil && cachedPerms != nil {
		log.Printf("Cache HIT: key=%s, permissions=%v", cacheKey, cachedPerms)
		result.Permissions = cachedPerms
		return result, nil
	}

	log.Printf("Cache MISS: key=%s, calling Jira API", cacheKey)

	permissions, err := s.checkJiraPermissions(ctx, parsedFIT, req.SystemToken, scope)
	if err != nil {
		if errors.Is(err, ErrMissingSystemToken) {
			log.Printf("Configuration fault for accountId=%s: %v", parsedFIT.AccountID, err)
			return nil, err
		}

		log.Printf("Jira API error: %v, attempting stale cache fallback", err)

		stalePerms, staleErr := s.cache.GetStale(ctx, cacheKey)
		if staleErr == nil && stalePerms != nil {
			log.Printf("Stale cache fallback: key=%s, permissions=%v", cacheKey, stalePerms)
			result.Permissions = stalePerms
			return result, nil
		}

		log.Printf("No stale cache available, returning empty permissions")
		return result, nil
	}

	if err := s.cache.Set(ctx, cacheKey, permissions); err != nil {
		log.Printf("Failed to cache permissions: %v", err)
	}

	log.Printf("Jira API success: permissions=%v", permissions)
	result.Permissions = permissions
	return result, nil
}

func parseContextScope(issueID, projectID string) (contextScope, error) {
	scope := contextScope{
		issuePresent:   issueID != "",
		projectPresent: projectID != "",
	}

	if scope.issuePresent {
		parsed, err := strconv.ParseInt(issueID, 10, 64)
		if err != nil {
			return contextScope{}, fmt.Errorf("x-issue-id is not numeric: %s", issueID)
		}
		scope.issueID = parsed
	}

	if scope.projectPresent {
		parsed, err := strconv.ParseInt(projectID, 10, 64)
		if err != nil {
			return contextScope{}, fmt.Errorf("x-project-id is not numeric: %s", projectID)
		}
		scope.projectID = parsed
	}

	return scope, nil
}

func (s *AuthzService) checkJiraPermissions(ctx context.Context, parsedFIT *fit.ParsedFIT, systemToken string, scope contextScope) ([]string, error) {
	if systemToken == "" {
		return nil, ErrMissingSystemToken
	}

	permissionARIs := buildPermissionARIs(parsedFIT.AppID, parsedFIT.EnvironmentID)

	projectPerms := jira.BulkProjectPermissions{
		Permissions: permissionARIs,
	}

	switch {
	case scope.issuePresent:
		projectPerms.Issues = []int64{scope.issueID}
	case scope.projectPresent:
		projectPerms.Projects = []int64{scope.projectID}
	default:
		return nil, fmt.Errorf("no issue or project context to scope the permission check")
	}

	jiraReq := &jira.BulkPermissionsRequestBean{
		AccountID:          parsedFIT.AccountID,
		ProjectPermissions: []jira.BulkProjectPermissions{projectPerms},
	}

	grantedARIs, err := s.jiraClient.CheckPermissions(ctx, parsedFIT.CloudID, systemToken, jiraReq)
	if err != nil {
		return nil, err
	}

	return mapARIsToBareKeys(grantedARIs, parsedFIT.AppID, parsedFIT.EnvironmentID), nil
}

func buildPermissionARIs(appID, environmentID string) []string {
	prefix := fmt.Sprintf("ari:cloud:ecosystem::extension/%s/%s/static/", appID, environmentID)
	return []string{
		prefix + viewMeetingKey,
		prefix + editMeetingKey,
	}
}

// mapARIsToBareKeys reduces granted permission identifiers to the bare manifest
// keys the downstream services match on. The result is always non-nil so a user
// holding no permissions is cached as an empty JSON array rather than null,
// which would never satisfy the cache-hit check.
func mapARIsToBareKeys(grantedARIs []string, appID, environmentID string) []string {
	prefix := fmt.Sprintf("ari:cloud:ecosystem::extension/%s/%s/static/", appID, environmentID)
	bareKeys := []string{}

	for _, ari := range grantedARIs {
		if !strings.HasPrefix(ari, prefix) {
			log.Printf("Ignoring granted permission without expected prefix: %s", ari)
			continue
		}

		bareKey := strings.TrimPrefix(ari, prefix)
		if bareKey != viewMeetingKey && bareKey != editMeetingKey {
			log.Printf("Ignoring unrecognized granted permission: %s", ari)
			continue
		}

		bareKeys = append(bareKeys, bareKey)
	}

	return bareKeys
}

// generateCacheKey qualifies the context with its kind so an issue and a
// project sharing a numeric identifier never collide.
func (s *AuthzService) generateCacheKey(cloudID, accountID, issueID, projectID string) string {
	context := ""
	if issueID != "" {
		context = "issue:" + issueID
	} else if projectID != "" {
		context = "project:" + projectID
	}

	return s.cache.GenerateKey(cloudID, accountID, context)
}
