import { z } from 'zod';
import type { MeetingManagementMeetingSettingsResponse } from '@/generated/types.gen.ts';

export const ADMISSION_POLICY_WAITING_ROOM = 'MANUAL_APPROVAL';
export const ADMISSION_POLICY_OPEN = 'ALLOW_ALL';

export const meetingSettingsSchema = z.object({
    waitingRoom: z.boolean(),
    allowGuest: z.boolean(),
    maxParticipants: z.number().int().min(2).max(500),
    allowScreenShare: z.boolean(),
    chatEnabled: z.boolean(),
    allowMicrophone: z.boolean(),
    allowVideo: z.boolean(),
    password: z.string().optional(),
});

export type MeetingSettingsValues = z.infer<typeof meetingSettingsSchema>;

export const MEETING_SETTINGS_DEFAULTS: MeetingSettingsValues = {
    waitingRoom: true,
    allowGuest: true,
    maxParticipants: 100,
    allowScreenShare: true,
    chatEnabled: true,
    allowMicrophone: true,
    allowVideo: true,
    password: undefined,
};

export const instantMeetingSchema = z.object({
    title: z.string().optional(),
    settings: meetingSettingsSchema,
});

export type InstantMeetingValues = z.infer<typeof instantMeetingSchema>;

export const scheduleMeetingSchema = z
    .object({
        title: z.string().optional(),
        description: z.string().optional(),
        date: z.string().min(1, 'required'),
        time: z.string().min(1, 'required'),
        durationMinutes: z.number().int().min(15),
        invitees: z.array(z.string().email()),
        settings: meetingSettingsSchema,
    })
    .refine(
        (data) => {
            const startTime = new Date(`${data.date}T${data.time}`);
            return startTime > new Date();
        },
        {
            message: 'startTimeMustBeFuture',
            path: ['date'],
        },
    );

export type ScheduleMeetingValues = z.infer<typeof scheduleMeetingSchema>;

export function mapSettingsToRequest(settings: MeetingSettingsValues) {
    return {
        admissionPolicy: settings.waitingRoom
            ? ADMISSION_POLICY_WAITING_ROOM
            : ADMISSION_POLICY_OPEN,
        allowGuest: settings.allowGuest,
        maxParticipants: settings.maxParticipants,
        allowScreenShare: settings.allowScreenShare,
        chatEnabled: settings.chatEnabled,
        allowMicrophone: settings.allowMicrophone,
        allowVideo: settings.allowVideo,
        ...(settings.password ? { password: settings.password } : {}),
    };
}

export function mapResponseToSettings(
    response: MeetingManagementMeetingSettingsResponse,
): MeetingSettingsValues {
    return {
        waitingRoom: response.admissionPolicy === ADMISSION_POLICY_WAITING_ROOM,
        allowGuest: response.allowGuest ?? MEETING_SETTINGS_DEFAULTS.allowGuest,
        maxParticipants:
            response.maxParticipants
            ?? MEETING_SETTINGS_DEFAULTS.maxParticipants,
        allowScreenShare:
            response.allowScreenShare
            ?? MEETING_SETTINGS_DEFAULTS.allowScreenShare,
        chatEnabled:
            response.chatEnabled ?? MEETING_SETTINGS_DEFAULTS.chatEnabled,
        allowMicrophone:
            response.allowMicrophone
            ?? MEETING_SETTINGS_DEFAULTS.allowMicrophone,
        allowVideo: response.allowVideo ?? MEETING_SETTINGS_DEFAULTS.allowVideo,
        password: undefined,
    };
}
