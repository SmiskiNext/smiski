/**
 * Date/time + timezone helpers for scheduling meetings.
 *
 * A `<input type="date">` + `<input type="time">` give a wall-clock date/time
 * with no timezone. To turn "2026-07-20 14:30 in Asia/Tokyo" into a correct UTC
 * instant we compute the target zone's offset for that wall time (via Intl),
 * without pulling in a date library.
 */

/** The viewer's own IANA timezone, e.g. `Asia/Ho_Chi_Minh`. */
export function getLocalTimeZone(): string {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    } catch {
        return 'UTC';
    }
}

/** True when `timeZone` is a resolvable IANA zone id (region-based, not a bare offset). */
function isResolvableTimeZone(timeZone: string): boolean {
    try {
        new Intl.DateTimeFormat('en-US', { timeZone }).format();
        return true;
    } catch {
        return false;
    }
}

/**
 * Resolve the meeting time zone from the invoking user's Jira profile zone.
 * Returns the profile zone when it is a resolvable IANA id, otherwise falls
 * back to the browser's local zone.
 */
export function resolveUserTimeZone(profileZone?: string): string {
    if (profileZone && isResolvableTimeZone(profileZone)) return profileZone;
    return getLocalTimeZone();
}

/** Common business timezones offered in the picker (local zone is prepended). */
const COMMON_TIME_ZONES = [
    'UTC',
    'Asia/Ho_Chi_Minh',
    'Asia/Bangkok',
    'Asia/Singapore',
    'Asia/Kolkata',
    'Asia/Dubai',
    'Asia/Shanghai',
    'Asia/Tokyo',
    'Australia/Sydney',
    'Europe/London',
    'Europe/Paris',
    'Europe/Berlin',
    'Europe/Moscow',
    'America/Sao_Paulo',
    'America/New_York',
    'America/Chicago',
    'America/Denver',
    'America/Los_Angeles',
];

/** Timezone options, viewer's own zone first, de-duplicated. */
export function listTimeZones(): string[] {
    const local = getLocalTimeZone();
    return Array.from(new Set([local, ...COMMON_TIME_ZONES]));
}

/** Current short offset label for a zone, e.g. `GMT+7`. */
export function timeZoneOffsetLabel(
    timeZone: string,
    at: Date = new Date(),
): string {
    try {
        const parts = new Intl.DateTimeFormat('en-US', {
            timeZone,
            timeZoneName: 'shortOffset',
        }).formatToParts(at);
        return parts.find((part) => part.type === 'timeZoneName')?.value ?? '';
    } catch {
        return '';
    }
}

/** Human label for a zone option, e.g. `(GMT+7) Asia/Ho Chi Minh`. */
export function formatTimeZoneOption(timeZone: string): string {
    const offset = timeZoneOffsetLabel(timeZone);
    const name = timeZone.replace(/_/g, ' ');
    return offset ? `(${offset}) ${name}` : name;
}

/**
 * The current wall-clock date/time as it reads on a clock in `timeZone` right
 * now, as `<input type="date">`/`<input type="time">`-compatible strings.
 * Used to set `min` on the scheduling inputs so users can't pick a past
 * instant in the meeting's own timezone (not just their local one).
 */
export function nowWallTimeInZone(timeZone: string): {
    date: string;
    time: string;
} {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    }).formatToParts(new Date());
    const get = (type: string) =>
        parts.find((part) => part.type === type)?.value ?? '';
    return {
        date: `${get('year')}-${get('month')}-${get('day')}`,
        time: `${get('hour')}:${get('minute')}`,
    };
}

/**
 * Convert a wall-clock `date` (YYYY-MM-DD) + `time` (HH:mm) interpreted in
 * `timeZone` into a UTC ISO string. Returns '' if inputs are unparseable.
 */
export function zonedWallTimeToIso(
    date: string,
    time: string,
    timeZone: string,
): string {
    if (!date || !time) return '';
    // Treat the wall time as if it were UTC, then correct by the zone's offset.
    const naiveUtc = new Date(`${date}T${time}:00Z`);
    if (Number.isNaN(naiveUtc.getTime())) return '';
    const tzView = new Date(naiveUtc.toLocaleString('en-US', { timeZone }));
    const utcView = new Date(
        naiveUtc.toLocaleString('en-US', { timeZone: 'UTC' }),
    );
    const offsetMs = tzView.getTime() - utcView.getTime();
    return new Date(naiveUtc.getTime() - offsetMs).toISOString();
}
