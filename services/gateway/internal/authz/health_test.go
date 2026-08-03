package authz

import (
	"context"
	"errors"
	"testing"

	"google.golang.org/grpc"
	"google.golang.org/grpc/health/grpc_health_v1"
)

type stubPinger struct {
	err error
}

func (s *stubPinger) Ping(ctx context.Context) error {
	return s.err
}

type stubWatchServer struct {
	grpc.ServerStream
	sent    []*grpc_health_v1.HealthCheckResponse
	sendErr error
}

func (s *stubWatchServer) Send(resp *grpc_health_v1.HealthCheckResponse) error {
	if s.sendErr != nil {
		return s.sendErr
	}
	s.sent = append(s.sent, resp)
	return nil
}

func (s *stubWatchServer) Context() context.Context {
	return context.Background()
}

func TestHealthChecker_CheckServingWhenCacheReachable(t *testing.T) {
	checker := NewHealthChecker(&stubPinger{})

	resp, err := checker.Check(context.Background(), &grpc_health_v1.HealthCheckRequest{})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if resp.GetStatus() != grpc_health_v1.HealthCheckResponse_SERVING {
		t.Errorf("expected SERVING, got %s", resp.GetStatus())
	}
}

func TestHealthChecker_CheckNotServingOnPingFailure(t *testing.T) {
	checker := NewHealthChecker(&stubPinger{err: errors.New("cache unreachable")})

	resp, err := checker.Check(context.Background(), &grpc_health_v1.HealthCheckRequest{})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if resp.GetStatus() != grpc_health_v1.HealthCheckResponse_NOT_SERVING {
		t.Errorf("expected NOT_SERVING, got %s", resp.GetStatus())
	}
}

func TestHealthChecker_WatchSendsServing(t *testing.T) {
	checker := NewHealthChecker(&stubPinger{})
	server := &stubWatchServer{}

	if err := checker.Watch(&grpc_health_v1.HealthCheckRequest{}, server); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}

	if len(server.sent) != 1 {
		t.Fatalf("expected 1 response sent, got %d", len(server.sent))
	}

	if server.sent[0].GetStatus() != grpc_health_v1.HealthCheckResponse_SERVING {
		t.Errorf("expected SERVING, got %s", server.sent[0].GetStatus())
	}
}

func TestHealthChecker_WatchPropagatesSendError(t *testing.T) {
	checker := NewHealthChecker(&stubPinger{})
	sendErr := errors.New("stream closed")

	if err := checker.Watch(&grpc_health_v1.HealthCheckRequest{}, &stubWatchServer{sendErr: sendErr}); !errors.Is(err, sendErr) {
		t.Errorf("expected send error to propagate, got %v", err)
	}
}
