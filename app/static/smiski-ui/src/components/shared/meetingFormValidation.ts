export const MEETING_TITLE_MAX_LENGTH = 255;

export function meetingTitleError(value?: string): string | undefined {
    if (!value?.trim()) return 'Enter a meeting title.';
    if (value.length > MEETING_TITLE_MAX_LENGTH) {
        return `Title must be ${MEETING_TITLE_MAX_LENGTH} characters or fewer.`;
    }
    return undefined;
}

export function meetingDescriptionError(value?: string): string | undefined {
    return value?.trim() ? undefined : 'Enter a meeting description.';
}

/** Prevents synthetic addresses from reaching the notification service. */
export function meetingEmailError(
    organizerEmail: string | undefined,
    invitees: readonly { email?: string }[],
): string | undefined {
    if (!organizerEmail?.trim()) {
        return 'Your Jira profile does not expose an email address, so Smiski cannot send meeting notifications.';
    }
    if (invitees.some((invitee) => !invitee.email?.trim())) {
        return 'Remove invitees whose Jira email address is unavailable.';
    }
    return undefined;
}

/** Mirrors the scheduling constraints before the SDK validates ISO formats. */
export function meetingTimeRangeError(
    startIso: string,
    endIso: string,
    now = Date.now(),
): string | undefined {
    const start = new Date(startIso).getTime();
    if (!startIso || Number.isNaN(start)) {
        return 'Choose a valid start date and time.';
    }
    if (start <= now) return 'Choose a start date and time in the future.';

    const end = new Date(endIso).getTime();
    if (!endIso || Number.isNaN(end)) {
        return 'Choose a valid end date and time.';
    }
    if (end <= start) return 'End time must be later than start time.';
    return undefined;
}
