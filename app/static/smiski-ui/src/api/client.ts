import { invoke } from '@forge/bridge';
import { apiConfig } from './config';

export interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  /** Parsed JSON body. */
  body?: unknown;
  /** Query string params. */
  query?: Record<string, string | number | boolean | undefined>;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

export interface ApiProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  traceId?: string;
  errors?: Array<{ field?: string; code?: string; message?: string | null }>;
}

interface BackendRequestResult {
  ok: boolean;
  status: number;
  body: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly traceId?: string;
  readonly problem?: ApiProblemDetail;

  constructor(status: number, body: unknown) {
    const problem = isProblemDetail(body) ? body : undefined;
    super(problem?.detail || problem?.title || `Smiski API request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem?.code;
    this.traceId = problem?.traceId;
    this.problem = problem;
  }
}

/**
 * Perform an authenticated request against the backend API gateway.
 * @typeParam T - expected response payload shape.
 */
export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const requestPath = buildApiPath(path, options.query);
  if (apiConfig.transport === 'direct') {
    return directRequest<T>(requestPath, options);
  }

  const result = (await invoke('backendRequest', {
    path: requestPath,
    options: {
      method: options.method,
      body: options.body,
      headers: options.headers,
    },
  })) as BackendRequestResult;

  if (!result.ok) throw new ApiError(result.status, result.body);
  return result.body as T;
}

function buildApiPath(
  path: string,
  query?: Record<string, string | number | boolean | undefined>,
): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const versionedPath = normalizedPath.startsWith('/api/')
    ? normalizedPath
    : `/api/${apiConfig.apiVersion}${normalizedPath}`;
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  const queryString = params.toString();
  return queryString ? `${versionedPath}?${queryString}` : versionedPath;
}

async function directRequest<T>(path: string, options: ApiRequestOptions): Promise<T> {
  if (!apiConfig.baseUrl) {
    throw new Error(
      'VITE_SMISKI_API_BASE_URL is required when VITE_SMISKI_API_TRANSPORT=direct.',
    );
  }

  const response = await fetch(`${apiConfig.baseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers: buildHeaders(options),
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  });

  const body = await parseBody(response);
  if (!response.ok) throw new ApiError(response.status, body);
  return body as T;
}

function buildHeaders(options: ApiRequestOptions): HeadersInit {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options.headers,
  };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  return headers;
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function isProblemDetail(value: unknown): value is ApiProblemDetail {
  return Boolean(value && typeof value === 'object' && 'status' in value);
}
