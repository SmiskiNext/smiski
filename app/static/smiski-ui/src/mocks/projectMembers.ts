/**
 * Mock project members — used only in standalone `vite dev`, where the real
 * `requestJira` bridge is not available. Mirrors the shape returned by
 * `api/projectMembers.getProjectMembers` so the hook can swap between them by
 * env, same pattern as `mocks/issues.ts`.
 */
import type { ProjectMember } from '../domain';
import { MOCK_USERS } from './users';

const NETWORK_DELAY_MS = 200;

export async function listProjectMembers(
  _projectKey: string,
  maxResults = 50,
): Promise<ProjectMember[]> {
  const result = MOCK_USERS.slice(0, maxResults);
  return new Promise((resolve) => setTimeout(() => resolve(result), NETWORK_DELAY_MS));
}
