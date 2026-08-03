package cache

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"net"
	"sync"
	"time"

	"github.com/valkey-io/valkey-go"
)

const (
	staleKeySuffix = ":stale"
	connectTimeout = 2 * time.Second
)

// Options describes how the cache connects to a Valkey deployment. TLS and the
// RBAC credentials cover managed endpoints such as AWS ElastiCache, where
// in-transit encryption is required and access is granted to a named user.
type Options struct {
	Addr           string
	Username       string
	Password       string
	TLS            bool
	TTL            time.Duration
	StaleRetention time.Duration
}

// CredentialProvider supplies the credentials used for each new connection.
// Returning fresh values on every call is what allows short-lived secrets, such
// as ElastiCache IAM authentication tokens, to be adopted without a restart.
type CredentialProvider func(ctx CredentialContext) (Credentials, error)

// CredentialContext carries the connection being authenticated.
type CredentialContext struct {
	Address string
}

// Credentials is the username and password pair applied to a connection.
type Credentials struct {
	Username string
	Password string
}

// Cache stores permission check results in Valkey.
//
// The client is created lazily so the gateway starts even when Valkey is
// unreachable, and is rebuilt on the next operation once the deployment
// recovers. Connection attempts are bounded by connectTimeout so an outage
// cannot stall callers waiting to acquire the client. Every entry is written
// twice: the primary key expires at the configured TTL and drives normal
// lookups, while a companion key retained for a longer window backs the
// stale-read fallback used when the Jira API fails.
type Cache struct {
	options            Options
	credentialProvider CredentialProvider

	mu     sync.Mutex
	client valkey.Client
}

// NewCache builds a cache bound to the given Valkey deployment. It performs no
// I/O; the connection is established on first use.
func NewCache(options Options) *Cache {
	cache := &Cache{options: options}
	cache.credentialProvider = cache.staticCredentials
	return cache
}

// WithCredentialProvider replaces the static credential lookup, allowing
// rotating secrets to be resolved per connection attempt.
func (c *Cache) WithCredentialProvider(provider CredentialProvider) *Cache {
	c.mu.Lock()
	defer c.mu.Unlock()

	c.credentialProvider = provider
	c.closeClientLocked()

	return c
}

func (c *Cache) staticCredentials(CredentialContext) (Credentials, error) {
	return Credentials{
		Username: c.options.Username,
		Password: c.options.Password,
	}, nil
}

func (c *Cache) clientOption() valkey.ClientOption {
	provider := c.credentialProvider

	option := valkey.ClientOption{
		InitAddress:  []string{c.options.Addr},
		Username:     c.options.Username,
		Password:     c.options.Password,
		Dialer:       net.Dialer{Timeout: connectTimeout},
		DisableCache: true,
		DisableRetry: true,
		AuthCredentialsFn: func(ctx valkey.AuthCredentialsContext) (valkey.AuthCredentials, error) {
			address := ""
			if ctx.Address != nil {
				address = ctx.Address.String()
			}

			credentials, err := provider(CredentialContext{Address: address})
			if err != nil {
				return valkey.AuthCredentials{}, err
			}

			return valkey.AuthCredentials{
				Username: credentials.Username,
				Password: credentials.Password,
			}, nil
		},
	}

	if c.options.TLS {
		option.TLSConfig = &tls.Config{MinVersion: tls.VersionTLS12}
	}

	return option
}

func (c *Cache) connect() (valkey.Client, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.client != nil {
		return c.client, nil
	}

	client, err := valkey.NewClient(c.clientOption())
	if err != nil {
		return nil, fmt.Errorf("cache connect error: %w", err)
	}

	c.client = client

	return client, nil
}

func (c *Cache) discard(client valkey.Client) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.client == client {
		c.closeClientLocked()
	}
}

func (c *Cache) closeClientLocked() {
	if c.client == nil {
		return
	}

	c.client.Close()
	c.client = nil
}

func (c *Cache) staleKey(key string) string {
	return key + staleKeySuffix
}

// Get returns the cached permissions for key, or nil when no fresh entry
// exists. A Valkey outage surfaces as an error so the caller can fall back to
// the Jira API.
func (c *Cache) Get(ctx context.Context, key string) ([]string, error) {
	permissions, err := c.read(ctx, key)
	if valkey.IsValkeyNil(err) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("cache get error: %w", err)
	}

	return permissions, nil
}

// GetStale returns the retained copy of the permissions, which outlives the
// primary entry so it can serve requests while the Jira API is unavailable.
func (c *Cache) GetStale(ctx context.Context, key string) ([]string, error) {
	return c.read(ctx, c.staleKey(key))
}

func (c *Cache) read(ctx context.Context, key string) ([]string, error) {
	client, err := c.connect()
	if err != nil {
		return nil, err
	}

	value, err := client.Do(ctx, client.B().Get().Key(key).Build()).ToString()
	if err != nil {
		if !valkey.IsValkeyNil(err) {
			c.discard(client)
		}
		return nil, err
	}

	var permissions []string
	if err := json.Unmarshal([]byte(value), &permissions); err != nil {
		return nil, fmt.Errorf("cache unmarshal error: %w", err)
	}

	return permissions, nil
}

// Set stores the permissions as a JSON array under the primary key with the
// configured TTL, alongside a longer-lived copy used for stale reads.
func (c *Cache) Set(ctx context.Context, key string, permissions []string) error {
	data, err := json.Marshal(permissions)
	if err != nil {
		return fmt.Errorf("cache marshal error: %w", err)
	}

	client, err := c.connect()
	if err != nil {
		return err
	}

	value := string(data)
	responses := client.DoMulti(ctx,
		client.B().Set().Key(key).Value(value).Ex(c.options.TTL).Build(),
		client.B().Set().Key(c.staleKey(key)).Value(value).Ex(c.options.StaleRetention).Build(),
	)

	for _, response := range responses {
		if err := response.Error(); err != nil {
			c.discard(client)
			return fmt.Errorf("cache set error: %w", err)
		}
	}

	return nil
}

// GenerateKey builds the permission key identifying a user's access to a
// resource within a tenant.
func (c *Cache) GenerateKey(cloudID, accountID, issueID string) string {
	return fmt.Sprintf("perm:%s:%s:%s", cloudID, accountID, issueID)
}

// Ping reports whether the Valkey deployment is reachable.
func (c *Cache) Ping(ctx context.Context) error {
	client, err := c.connect()
	if err != nil {
		return err
	}

	if err := client.Do(ctx, client.B().Ping().Build()).Error(); err != nil {
		c.discard(client)
		return err
	}

	return nil
}

// Close releases the connection to Valkey.
func (c *Cache) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	c.closeClientLocked()

	return nil
}
