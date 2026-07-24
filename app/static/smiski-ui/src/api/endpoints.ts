function encodePathSegment(value: string): string {
    return encodeURIComponent(value);
}

export const meetingEndpoints = {
    list: '/meetings',
    byId: (meetingId: string) => `/meetings/${encodePathSegment(meetingId)}`,
    createInstant: '/meetings:instant',
    schedule: '/meetings:schedule',
    update: (meetingId: string) => `/meetings/${encodePathSegment(meetingId)}`,
    updateInvitees: (meetingId: string) =>
        `/meetings/${encodePathSegment(meetingId)}/invitees`,
    cancel: (meetingId: string) =>
        `/meetings/${encodePathSegment(meetingId)}:cancel`,
    start: (meetingId: string) =>
        `/meetings/${encodePathSegment(meetingId)}:start`,
    end: (meetingId: string) => `/meetings/${encodePathSegment(meetingId)}:end`,
    participants: (meetingId: string) =>
        `/meetings/${encodePathSegment(meetingId)}/participants`,
    roomToken: (meetingId: string) =>
        `/meetings/${encodePathSegment(meetingId)}/room-token`,
    hostConflict: '/meetings/host-conflict',
};

export const permissionEndpoints = {
    projectMeetingPermissions: (projectKey: string) =>
        `/projects/${encodePathSegment(projectKey)}/meeting-permissions`,
};

export const recordingEndpoints = {
    byMeeting: (meetingId: string) =>
        `/meetings/${encodePathSegment(meetingId)}/recording`,
    start: (meetingId: string) =>
        `/meetings/${encodePathSegment(meetingId)}/recording:start`,
    stop: (meetingId: string) =>
        `/meetings/${encodePathSegment(meetingId)}/recording:stop`,
};
