package authz

import (
	"context"
	"fmt"
	"log"

	"github.com/smiskinext/gateway/internal/fit"
	"github.com/smiskinext/gateway/internal/jira"
)

type JiraClient interface {
	CheckPermissions(ctx context.Context, cloudID, systemToken string, req *jira.PermissionsCheckRequest) ([]string, error)
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
	ProjectKey  string
}

type AuthzResult struct {
	CloudID     string
	AccountID   string
	Permissions []string
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

	if req.IssueID == "" && req.ProjectKey == "" {
		log.Printf("No context headers present, skipping permission check for accountId=%s", parsedFIT.AccountID)
		return result, nil
	}

	cacheKey := s.generateCacheKey(parsedFIT.CloudID, parsedFIT.AccountID, req.IssueID, req.ProjectKey)

	cachedPerms, err := s.cache.Get(ctx, cacheKey)
	if err == nil && cachedPerms != nil {
		log.Printf("Cache HIT: key=%s, permissions=%v", cacheKey, cachedPerms)
		result.Permissions = cachedPerms
		return result, nil
	}

	log.Printf("Cache MISS: key=%s, calling Jira API", cacheKey)

	permissions, err := s.checkJiraPermissions(ctx, parsedFIT.CloudID, parsedFIT.AccountID, req)
	if err != nil {
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

func (s *AuthzService) checkJiraPermissions(ctx context.Context, cloudID, accountID string, req *AuthzRequest) ([]string, error) {
	if req.SystemToken == "" {
		return nil, fmt.Errorf("missing system token")
	}

	jiraReq := &jira.PermissionsCheckRequest{
		AccountID:   accountID,
		Permissions: []string{"view-meeting", "edit-meeting"},
	}

	if req.IssueID != "" {
		jiraReq.IssueID = req.IssueID
	} else if req.ProjectKey != "" {
		jiraReq.ProjectKey = req.ProjectKey
	}

	return s.jiraClient.CheckPermissions(ctx, cloudID, req.SystemToken, jiraReq)
}

func (s *AuthzService) generateCacheKey(cloudID, accountID, issueID, projectKey string) string {
	context := issueID
	if context == "" {
		context = projectKey
	}
	return s.cache.GenerateKey(cloudID, accountID, context)
}
