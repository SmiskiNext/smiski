/**
 * Per-user meeting room layout mode (Auto / Tiled / Spotlight), persisted in
 * `localStorage` so the choice survives a reload and the Project Page iframe
 * being torn down between visits.
 *
 * Only the MODE is remembered. A pin is deliberately left out: it names one
 * specific participant in one specific call, and restoring it into a later
 * meeting they are not in would just resolve back to unpinned.
 *
 * Kept out of the backend meeting settings on purpose: this is a personal
 * viewing preference, not part of the meeting a host configures. Reads and
 * writes are best-effort — private browsing or a blocked storage partition
 * falls back to the default Auto layout rather than failing the room.
 */
import { isLayoutMode, type LayoutMode } from '../domain/meetingLayout';

const STORAGE_KEY = 'smiski:meeting-layout';

const DEFAULT_MODE: LayoutMode = 'auto';

/** Absent, unrecognized or unavailable storage all mean "let the room decide". */
export function readMeetingLayoutMode(): LayoutMode {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return isLayoutMode(raw) ? raw : DEFAULT_MODE;
    } catch {
        return DEFAULT_MODE;
    }
}

export function writeMeetingLayoutMode(mode: LayoutMode): void {
    try {
        localStorage.setItem(STORAGE_KEY, mode);
    } catch {
        // Best-effort only (private browsing / storage quota) — the chosen
        // layout still applies for the rest of this session, it just won't be
        // remembered the next time the room opens.
    }
}
