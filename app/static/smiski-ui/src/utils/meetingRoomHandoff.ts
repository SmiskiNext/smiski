/**
 * Issue Panel and Project Page are separate Forge modules — separate iframes,
 * no shared React tree — so "open this meeting's room" can't be passed as a
 * prop or router param the way an SPA would. Both modules share the same
 * origin (one Custom UI bundle), so `localStorage` is the handoff: the Issue
 * Panel writes the target meeting before navigating away, and the Project
 * Page checks for it once on mount.
 */
const STORAGE_KEY = 'smiski:pending-meeting-room';

interface PendingMeetingRoom {
  projectKey: string;
  meetingId: string;
}

export function setPendingMeetingRoom(projectKey: string, meetingId: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ projectKey, meetingId }));
  } catch {
    // Best-effort only (private browsing / storage quota) — the Project Page
    // just falls back to its normal dashboard view instead of auto-opening.
  }
}

/** Reads and clears the pending handoff, scoped to the given project. */
export function consumePendingMeetingRoom(projectKey: string): string | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    localStorage.removeItem(STORAGE_KEY);
    const parsed = JSON.parse(raw) as PendingMeetingRoom;
    return parsed.projectKey === projectKey ? parsed.meetingId : null;
  } catch {
    return null;
  }
}
