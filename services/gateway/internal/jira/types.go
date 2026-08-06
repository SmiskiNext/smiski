package jira

// BulkProjectPermissions is one entry of the permission check request, pairing
// the permission identifiers with the issue or project context they are
// evaluated against.
type BulkProjectPermissions struct {
	Permissions []string `json:"permissions"`
	Issues      []int64  `json:"issues,omitempty"`
	Projects    []int64  `json:"projects,omitempty"`
}

// BulkPermissionsRequestBean is the Jira permission check request body. The
// published schema sets additionalProperties to false, so no member outside
// accountId, globalPermissions and projectPermissions may be serialised.
type BulkPermissionsRequestBean struct {
	AccountID          string                   `json:"accountId"`
	GlobalPermissions  []string                 `json:"globalPermissions,omitempty"`
	ProjectPermissions []BulkProjectPermissions `json:"projectPermissions,omitempty"`
}

// BulkProjectPermissionGrants is one granted project permission. The schema
// carries no boolean: presence of the entry is itself the grant.
type BulkProjectPermissionGrants struct {
	Permission string  `json:"permission"`
	Issues     []int64 `json:"issues,omitempty"`
	Projects   []int64 `json:"projects,omitempty"`
}

// BulkPermissionGrants is the Jira permission check response. Both members are
// required by the schema, so they are decoded through pointers to distinguish
// an absent member from an empty one.
type BulkPermissionGrants struct {
	GlobalPermissions  *[]string                      `json:"globalPermissions"`
	ProjectPermissions *[]BulkProjectPermissionGrants `json:"projectPermissions"`
}

// ErrorResponse is the Jira error body returned alongside a 4xx status.
type ErrorResponse struct {
	ErrorMessages []string          `json:"errorMessages"`
	Errors        map[string]string `json:"errors"`
}
