/**
 * Mocks barrel — frontend-only dev fixtures. Meeting persistence itself is
 * never mocked (it goes through the real `meet` backend); these fixtures back
 * other dev-only, no-Forge-bridge scenarios (`CURRENT_USER` for standalone
 * `vite dev`, seeded Jira issues).
 */

export * from './issues';
export * from './users';
