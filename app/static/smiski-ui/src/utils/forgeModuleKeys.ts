/**
 * Forge module keys — must match the module `key`s declared in manifest.yml.
 * Single source of truth so App.tsx's context-based module detection and any
 * cross-module navigation (e.g. `hooks/useNavigateToMeetingRoom.ts`) can't
 * drift apart.
 */
export const MODULE_KEY_ISSUE_CONTEXT = 'smiski-issue-context';
export const MODULE_KEY_PROJECT_PAGE = 'smiski-project-page';
