package authz

import (
	"context"
	"encoding/json"
	"log"
	"strings"

	envoy_api_v3_core "github.com/envoyproxy/go-control-plane/envoy/config/core/v3"
	envoy_service_auth_v3 "github.com/envoyproxy/go-control-plane/envoy/service/auth/v3"
	envoy_type_v3 "github.com/envoyproxy/go-control-plane/envoy/type/v3"
	"google.golang.org/genproto/googleapis/rpc/status"
	"google.golang.org/grpc/codes"
)

type AuthzServiceInterface interface {
	Authorize(ctx context.Context, req *AuthzRequest) (*AuthzResult, error)
}

type Server struct {
	envoy_service_auth_v3.UnimplementedAuthorizationServer
	authzService AuthzServiceInterface
}

func NewServer(authzService AuthzServiceInterface) *Server {
	return &Server{
		authzService: authzService,
	}
}

func (s *Server) Check(ctx context.Context, req *envoy_service_auth_v3.CheckRequest) (*envoy_service_auth_v3.CheckResponse, error) {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("Panic recovered in Check: %v", r)
		}
	}()

	headers := req.GetAttributes().GetRequest().GetHttp().GetHeaders()

	authHeader := getHeader(headers, "authorization")
	issueID := getHeader(headers, "x-issue-id")
	projectKey := getHeader(headers, "x-project-key")
	systemToken := getHeader(headers, "x-forge-oauth-system")

	log.Printf("Check request: issueID=%s, projectKey=%s, hasAuth=%v, hasSystemToken=%v",
		issueID, projectKey, authHeader != "", systemToken != "")

	if authHeader == "" {
		return s.buildDeniedResponse(401, "missing_authorization", "Authorization header required"), nil
	}

	authzReq := &AuthzRequest{
		FITToken:    authHeader,
		SystemToken: systemToken,
		IssueID:     issueID,
		ProjectKey:  projectKey,
	}

	result, err := s.authzService.Authorize(ctx, authzReq)
	if err != nil {
		log.Printf("Authorization failed: %v", err)
		return s.buildDeniedResponse(403, "authorization_failed", err.Error()), nil
	}

	log.Printf("Check response: status=OK, cloudID=%s, accountID=%s, permissions=%v",
		result.CloudID, result.AccountID, result.Permissions)

	return s.buildOkResponse(result), nil
}

func (s *Server) buildOkResponse(result *AuthzResult) *envoy_service_auth_v3.CheckResponse {
	headers := []*envoy_api_v3_core.HeaderValueOption{
		{
			Header: &envoy_api_v3_core.HeaderValue{
				Key:   "x-tenant-id",
				Value: result.CloudID,
			},
			AppendAction: envoy_api_v3_core.HeaderValueOption_OVERWRITE_IF_EXISTS_OR_ADD,
		},
		{
			Header: &envoy_api_v3_core.HeaderValue{
				Key:   "x-account-id",
				Value: result.AccountID,
			},
			AppendAction: envoy_api_v3_core.HeaderValueOption_OVERWRITE_IF_EXISTS_OR_ADD,
		},
		{
			Header: &envoy_api_v3_core.HeaderValue{
				Key:   "x-project-permissions",
				Value: strings.Join(result.Permissions, ","),
			},
			AppendAction: envoy_api_v3_core.HeaderValueOption_OVERWRITE_IF_EXISTS_OR_ADD,
		},
	}

	return &envoy_service_auth_v3.CheckResponse{
		Status: &status.Status{
			Code: int32(codes.OK),
		},
		HttpResponse: &envoy_service_auth_v3.CheckResponse_OkResponse{
			OkResponse: &envoy_service_auth_v3.OkHttpResponse{
				Headers: headers,
			},
		},
	}
}

func (s *Server) buildDeniedResponse(statusCode int32, errorCode, message string) *envoy_service_auth_v3.CheckResponse {
	errorBody := map[string]string{
		"error":   errorCode,
		"message": message,
	}

	bodyBytes, _ := json.Marshal(errorBody)

	grpcStatusCode := codes.PermissionDenied
	if statusCode == 401 {
		grpcStatusCode = codes.Unauthenticated
	}

	return &envoy_service_auth_v3.CheckResponse{
		Status: &status.Status{
			Code:    int32(grpcStatusCode),
			Message: message,
		},
		HttpResponse: &envoy_service_auth_v3.CheckResponse_DeniedResponse{
			DeniedResponse: &envoy_service_auth_v3.DeniedHttpResponse{
				Status: &envoy_type_v3.HttpStatus{
					Code: envoy_type_v3.StatusCode(statusCode),
				},
				Body: string(bodyBytes),
				Headers: []*envoy_api_v3_core.HeaderValueOption{
					{
						Header: &envoy_api_v3_core.HeaderValue{
							Key:   "content-type",
							Value: "application/json",
						},
						AppendAction: envoy_api_v3_core.HeaderValueOption_OVERWRITE_IF_EXISTS_OR_ADD,
					},
				},
			},
		},
	}
}

func getHeader(headers map[string]string, key string) string {
	if val, ok := headers[key]; ok {
		return val
	}
	lowerKey := strings.ToLower(key)
	for k, v := range headers {
		if strings.ToLower(k) == lowerKey {
			return v
		}
	}
	return ""
}
