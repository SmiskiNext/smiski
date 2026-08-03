package jira

type PermissionsCheckRequest struct {
	AccountID         string   `json:"accountId"`
	Permissions       []string `json:"permissions"`
	IssueID           string   `json:"issueId,omitempty"`
	ProjectKey        string   `json:"projectKey,omitempty"`
	GlobalPermissions []string `json:"globalPermissions,omitempty"`
}

type PermissionCheckResult struct {
	Key           string `json:"key"`
	HasPermission bool   `json:"hasPermission"`
}

type PermissionsCheckResponse struct {
	GlobalPermissions  []PermissionCheckResult `json:"globalPermissions,omitempty"`
	ProjectPermissions []PermissionCheckResult `json:"projectPermissions,omitempty"`
}

type ErrorResponse struct {
	ErrorMessages []string          `json:"errorMessages"`
	Errors        map[string]string `json:"errors"`
}
