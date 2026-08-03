package cache

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

type Cache struct {
	client *redis.Client
	ttl    time.Duration
}

func NewCache(addr, password string, ttl time.Duration) *Cache {
	client := redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
		DB:       0,
	})

	return &Cache{
		client: client,
		ttl:    ttl,
	}
}

func (c *Cache) Get(ctx context.Context, key string) ([]string, error) {
	val, err := c.client.Get(ctx, key).Result()
	if err == redis.Nil {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("cache get error: %w", err)
	}

	var permissions []string
	if err := json.Unmarshal([]byte(val), &permissions); err != nil {
		return nil, fmt.Errorf("cache unmarshal error: %w", err)
	}

	return permissions, nil
}

func (c *Cache) Set(ctx context.Context, key string, permissions []string) error {
	data, err := json.Marshal(permissions)
	if err != nil {
		return fmt.Errorf("cache marshal error: %w", err)
	}

	if err := c.client.Set(ctx, key, data, c.ttl).Err(); err != nil {
		return fmt.Errorf("cache set error: %w", err)
	}

	return nil
}

func (c *Cache) GetStale(ctx context.Context, key string) ([]string, error) {
	val, err := c.client.Get(ctx, key).Result()
	if err != nil {
		return nil, err
	}

	var permissions []string
	if err := json.Unmarshal([]byte(val), &permissions); err != nil {
		return nil, err
	}

	return permissions, nil
}

func (c *Cache) GenerateKey(cloudID, accountID, issueID string) string {
	return fmt.Sprintf("perm:%s:%s:%s", cloudID, accountID, issueID)
}

func (c *Cache) Ping(ctx context.Context) error {
	return c.client.Ping(ctx).Err()
}

func (c *Cache) Close() error {
	return c.client.Close()
}
