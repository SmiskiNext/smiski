const DEVICE_STORAGE_KEY = 'smiski:device-id';

type BackendEnvelope = Record<string, unknown>;

export function permissionsFromBackend(payload: unknown): {
    hasViewMeeting: boolean;
    hasEditMeeting: boolean;
} {
    if (!isEnvelope(payload))
        return { hasViewMeeting: false, hasEditMeeting: false };
    return {
        hasViewMeeting: Boolean(
            payload.hasViewMeeting
                ?? payload.canViewMeeting
                ?? payload.viewMeeting,
        ),
        hasEditMeeting: Boolean(
            payload.hasEditMeeting
                ?? payload.canEditMeeting
                ?? payload.editMeeting,
        ),
    };
}

/**
 * Stable per-browser device id for the host participant. Persisted in
 * localStorage; falls back to a fixed id when storage is unavailable.
 */
export function getDeviceId(): string {
    try {
        const existing = localStorage.getItem(DEVICE_STORAGE_KEY);
        if (existing) return existing;
        const value = `web-${crypto.randomUUID()}`;
        localStorage.setItem(DEVICE_STORAGE_KEY, value);
        return value;
    } catch {
        return 'web-forge-client';
    }
}

function isEnvelope(value: unknown): value is BackendEnvelope {
    return Boolean(value && typeof value === 'object');
}
