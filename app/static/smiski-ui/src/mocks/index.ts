/**
 * Mocks barrel — frontend-only dev fixtures. Meeting persistence itself is no
 * longer mocked (see `app/src/meetingStore.ts`'s Forge KVS store); these
 * remaining fixtures back other dev-only, no-Forge-bridge scenarios
 * (`CURRENT_USER` for standalone `vite dev`, seeded Jira issues).
 */

export * from './issues';
export * from './users';
