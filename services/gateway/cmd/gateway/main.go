package main

import (
	"context"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"

	envoy_service_auth_v3 "github.com/envoyproxy/go-control-plane/envoy/service/auth/v3"
	"github.com/smiskinext/gateway/internal/authz"
	"github.com/smiskinext/gateway/internal/cache"
	"github.com/smiskinext/gateway/internal/config"
	"github.com/smiskinext/gateway/internal/jira"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/reflection"
)

func main() {
	cfg := config.Load()

	log.Printf("Starting gateway service on port %s", cfg.GRPCPort)
	log.Printf("Config: RedisAddr=%s, JiraAPIBase=%s, CacheTTL=%s",
		cfg.RedisAddr, cfg.JiraAPIBase, cfg.CacheTTL)

	cacheClient := cache.NewCache(cfg.RedisAddr, cfg.RedisPassword, cfg.CacheTTL)
	if err := cacheClient.Ping(context.Background()); err != nil {
		log.Printf("WARNING: Failed to connect to cache: %v", err)
	} else {
		log.Println("Cache connection established")
	}

	jiraClient := jira.NewClient(cfg.JiraAPIBase)

	authzService := authz.NewAuthzService(jiraClient, cacheClient)
	authzServer := authz.NewServer(authzService)
	healthChecker := authz.NewHealthChecker(cacheClient)

	lis, err := net.Listen("tcp", ":"+cfg.GRPCPort)
	if err != nil {
		log.Fatalf("Failed to listen on port %s: %v", cfg.GRPCPort, err)
	}

	grpcServer := grpc.NewServer()

	envoy_service_auth_v3.RegisterAuthorizationServer(grpcServer, authzServer)
	grpc_health_v1.RegisterHealthServer(grpcServer, healthChecker)
	reflection.Register(grpcServer)

	log.Printf("gRPC server listening on :%s", cfg.GRPCPort)

	go func() {
		if err := grpcServer.Serve(lis); err != nil {
			log.Fatalf("Failed to serve gRPC: %v", err)
		}
	}()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
	<-sigChan

	log.Println("Shutting down gracefully...")
	grpcServer.GracefulStop()
	if err := cacheClient.Close(); err != nil {
		log.Printf("Failed to close cache connection: %v", err)
	}
	log.Println("Shutdown complete")
}
