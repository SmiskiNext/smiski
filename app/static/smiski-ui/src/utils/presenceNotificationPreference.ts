/**
 * Per-user toggle for the meeting room's join/leave toasts, persisted in
 * `localStorage` so the choice survives a reload and the Project Page iframe
 * being torn down between visits.
 *
 * Kept out of the backend meeting settings on purpose: this is a personal
 * viewing preference, not part of the meeting a host configures. Reads and
 * writes are best-effort — private browsing or a blocked storage partition
 * falls back to the default-on behavior rather than failing the room.
 */
const STORAGE_KEY = 'smiski:presence-notifications';

/** Absent, unparseable or unavailable storage all mean "notify me". */
export function readPresenceNotificationsEnabled(): boolean {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw === null ? true : raw !== 'false';
    } catch {
        return true;
    }
}

export function writePresenceNotificationsEnabled(enabled: boolean): void {
    try {
        localStorage.setItem(STORAGE_KEY, String(enabled));
    } catch {
        // Best-effort only (private browsing / storage quota) — the toggle
        // still applies for the rest of this session, it just won't be
        // remembered the next time the room opens.
    }
}
