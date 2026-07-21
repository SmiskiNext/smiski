/**
 * Kong Gateway client — base request wrapper (STUB).
 *
 * All backend access goes through the Kong Gateway carrying the invoking Jira
 * user's identity (Forge Remote / JWT-JWKS auth bridge — see architecture_vi.md).
 * The Forge app is a CLIENT only; it never talks to Postgres/Kafka/LiveKit-server
 * directly (the sole exception is the LiveKit client SDK inside the meeting room).
 *
 * TODO: implement using `@forge/bridge` `requestJira`-style egress or a Forge
 * `fetch` via a resolver. No fetch logic exists yet.
 */

export interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** Parsed JSON body. */
  body?: unknown;
  /** Query string params. */
  query?: Record<string, string | number | boolean | undefined>;
  signal?: AbortSignal;
}

/**
 * Perform an authenticated request against the Kong Gateway.
 * @typeParam T - expected response payload shape.
 */
export async function apiRequest<T>(_path: string, _options?: ApiRequestOptions): Promise<T> {
  // TODO: build URL from gateway base + path, attach identity, call, parse.
  throw new Error('Not implemented: apiRequest');
}
