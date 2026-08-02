/**
 * ScheduleMeetingModal — the shared schedule-meeting form. Chrome (backdrop,
 * card, header, footer) comes from the local `ui/Modal`; fields are still
 * Ant Design (`Form`, `Input`, `Select`, `Alert`) — see that component's
 * `chrome` doc comment for why a bare antd `Modal` isn't used here: its own
 * mask/backdrop rendered a stray dim layer when embedded inside a Forge
 * platform modal, since that modal already supplies the backdrop.
 *
 * The CREATE branch calls the real `meet` backend through Forge Remote
 * (`useScheduleMeeting`), capturing a start time and a time zone (seeded from
 * the invoking user's Jira profile), plus invitees carrying full identity
 * from `WorkspaceUserPicker`. The backend's `timeRange` still requires an end
 * time, so one is derived as `start + DEFAULT_MEETING_DURATION_MS` — the form
 * itself only asks for a start time. The EDIT branch also calls the real
 * backend (`useUpdateMeeting`), but the backend `update` operation is a full
 * replace, so it first fetches the meeting's full detail (`useMeeting`) to
 * carry forward `issueLink`/`settings`/`zoneId`/`endTime` unchanged — this
 * form only edits title/description/start-time. Backend failures are shown
 * inline and keep the modal open.
 */
import { Alert, Form, Input, Select } from 'antd';
import { useState } from 'react';
import type { CreateMeetingSettingsInput } from '../../api/meetings';
import { listProjectMeetings } from '../../api/meetings';
import type { WorkspaceUser } from '../../api/workspaceUsers';
import { useCurrentUser } from '../../context/CurrentUserContext';
import type { Meeting } from '../../domain';
import { useMeeting } from '../../hooks/useMeeting';
import {
    useScheduleMeeting,
    useUpdateMeeting,
} from '../../hooks/useMeetingMutations';
import {
    formatTimeZoneOption,
    listTimeZones,
    nowWallTimeInZone,
    resolveUserTimeZone,
    zonedWallTimeToIso,
} from '../../utils/datetime';
import { Button, Modal } from '../ui';
import { AdvancedMeetingSettingsFields } from './AdvancedMeetingSettingsFields';
import { IssuePicker } from './IssuePicker';
import { WorkspaceUserPicker } from './WorkspaceUserPicker';

const TIME_ZONE_OPTIONS = listTimeZones().map((zone) => ({
    value: zone,
    label: formatTimeZoneOption(zone),
}));

/** New meetings default to a 1-hour slot; the backend still requires an end time. */
const DEFAULT_MEETING_DURATION_MS = 60 * 60 * 1000;

/** Matches DEFAULT_MEETING_SETTINGS in api/meetings.ts. */
const DEFAULT_ADVANCED_SETTINGS: CreateMeetingSettingsInput = {
    admissionPolicy: 'ALLOW_ALL',
    maxParticipants: 50,
    allowScreenShare: true,
    allowMicrophone: true,
    allowVideo: true,
};

export interface ScheduleMeetingModalProps {
    isOpen: boolean;
    issueKey?: string;
    projectKey?: string;
    meeting?: Meeting;
    onClose: () => void;
    onSubmitted?: (meetingId: string) => void;
    /** Pass 'embedded' when already rendered inside a Forge platform Modal. */
    chrome?: 'overlay' | 'embedded';
}

interface ScheduleMeetingFormValues
    extends Partial<CreateMeetingSettingsInput> {
    issueKey?: string;
    title: string;
    startDate: string;
    startTime: string;
    description?: string;
}

/**
 * Bridges Ant Design `Form.Item`'s injected `value`/`onChange` to the
 * `IssuePicker` selection contract so the picked issue key is form-controlled.
 */
function IssueField({
    projectKey,
    value,
    onChange,
}: {
    projectKey: string;
    value?: string;
    onChange?: (issueKey: string) => void;
}) {
    return (
        <IssuePicker
            projectKey={projectKey}
            value={value ?? ''}
            autoFocus
            onChange={(key) => onChange?.(key)}
        />
    );
}

function wallTimeParts(iso?: string): { date: string; time: string } {
    if (!iso) return { date: '', time: '' };
    const at = new Date(iso);
    if (Number.isNaN(at.getTime())) return { date: '', time: '' };
    const pad = (value: number) => String(value).padStart(2, '0');
    return {
        date: `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}`,
        time: `${pad(at.getHours())}:${pad(at.getMinutes())}`,
    };
}

/**
 * Whether the invoking user already has another `SCHEDULED` meeting starting
 * at the same instant, scoped to meetings *this user scheduled* (not every
 * meeting in the project) — the business rule only guards against one person
 * double-booking their own calendar.
 */
async function hasOwnScheduleConflict(
    projectKey: string,
    organizerAccountId: string,
    startIso: string,
    excludingMeetingId?: string,
): Promise<boolean> {
    if (!projectKey) return false;
    // Standalone `vite dev` has no Forge bridge to reach the resolver
    // through — fail open (no conflict) rather than blocking the form.
    const ownMeetings = await listProjectMeetings({
        projectKey,
        createdByAccountId: organizerAccountId,
    }).catch(() => []);
    const startMs = new Date(startIso).getTime();
    return ownMeetings.some(
        (candidate) =>
            candidate.id !== excludingMeetingId
            && candidate.status === 'SCHEDULED'
            && candidate.scheduledAt !== undefined
            && new Date(candidate.scheduledAt).getTime() === startMs,
    );
}

export function ScheduleMeetingModal({
    isOpen,
    issueKey,
    projectKey,
    meeting,
    onClose,
    onSubmitted,
    chrome = 'overlay',
}: ScheduleMeetingModalProps) {
    const [form] = Form.useForm<ScheduleMeetingFormValues>();
    const currentUser = useCurrentUser();
    const scheduleMeeting = useScheduleMeeting();
    const updateMeeting = useUpdateMeeting();
    const isEdit = Boolean(meeting);
    // The backend `update` request is a full replace; fetch the current full
    // detail (settings/zoneId/endTime) this form doesn't itself edit so it can
    // be carried forward unchanged.
    const editDetail = useMeeting(isEdit ? meeting?.id : undefined);
    // Issue-context modal payloads normally include the project key, but derive
    // it from the linked issue as a defensive fallback so the picker/invitees
    // are never disabled merely because the optional context field was absent.
    const effectiveProjectKey =
        projectKey || meeting?.projectKey || issueKey?.split('-')[0] || '';
    const [invitees, setInvitees] = useState<WorkspaceUser[]>([]);
    const [timeZone, setTimeZone] = useState(
        resolveUserTimeZone(currentUser.timeZone),
    );
    const [formError, setFormError] = useState<string | null>(null);

    const start = wallTimeParts(meeting?.scheduledAt);
    const initialValues: Partial<ScheduleMeetingFormValues> = {
        title: meeting?.title ?? '',
        description: meeting?.description ?? '',
        startDate: start.date,
        startTime: start.time,
        ...(isEdit ? {} : DEFAULT_ADVANCED_SETTINGS),
    };

    const resetAndClose = () => {
        form.resetFields();
        setInvitees([]);
        setFormError(null);
        onClose();
    };

    const handleSubmit = async (values: ScheduleMeetingFormValues) => {
        setFormError(null);
        const title = values.title.trim();
        const description = values.description?.trim() ?? '';
        const startIso = zonedWallTimeToIso(
            values.startDate,
            values.startTime,
            timeZone,
        );
        if (!startIso) {
            setFormError('Choose a valid start date and time.');
            return;
        }
        if (new Date(startIso).getTime() <= Date.now()) {
            setFormError('Choose a start date and time in the future.');
            return;
        }
        if (
            await hasOwnScheduleConflict(
                effectiveProjectKey,
                currentUser.accountId,
                startIso,
                meeting?.id,
            )
        ) {
            setFormError(
                'Vui lòng không chọn thời gian bắt đầu cuộc họp trùng với thời gian bắt đầu cuộc họp đã lên lịch trước đó!',
            );
            return;
        }

        if (isEdit && meeting) {
            if (!editDetail.meeting) {
                setFormError(
                    'Meeting details are still loading — try again in a moment.',
                );
                return;
            }
            try {
                const updated = await updateMeeting.mutateAsync({
                    meetingId: meeting.id,
                    input: {
                        title,
                        description,
                        startTime: startIso,
                        detail: editDetail.meeting,
                    },
                });
                onSubmitted?.(updated.id);
                resetAndClose();
            } catch (error) {
                setFormError(
                    error instanceof Error
                        ? error.message
                        : 'Could not save the meeting.',
                );
            }
            return;
        }

        const endIso = new Date(
            new Date(startIso).getTime() + DEFAULT_MEETING_DURATION_MS,
        ).toISOString();

        const resolvedIssueKey = (issueKey ?? values.issueKey ?? '')
            .trim()
            .toUpperCase();
        const result = await scheduleMeeting.mutateAsync({
            issueKey: resolvedIssueKey,
            projectKey: effectiveProjectKey || undefined,
            title,
            description,
            startTime: startIso,
            endTime: endIso,
            zoneId: timeZone,
            invitees: invitees.map((user) => ({
                accountId: user.accountId,
                displayName: user.displayName,
                email: user.email,
            })),
            organizer: {
                accountId: currentUser.accountId,
                displayName: currentUser.displayName,
                email: currentUser.email,
                avatarUrl: currentUser.avatarUrl,
            },
            settings: {
                admissionPolicy:
                    values.admissionPolicy
                    ?? DEFAULT_ADVANCED_SETTINGS.admissionPolicy,
                maxParticipants:
                    values.maxParticipants
                    ?? DEFAULT_ADVANCED_SETTINGS.maxParticipants,
                allowScreenShare:
                    values.allowScreenShare
                    ?? DEFAULT_ADVANCED_SETTINGS.allowScreenShare,
                allowMicrophone:
                    values.allowMicrophone
                    ?? DEFAULT_ADVANCED_SETTINGS.allowMicrophone,
                allowVideo:
                    values.allowVideo ?? DEFAULT_ADVANCED_SETTINGS.allowVideo,
            },
        });

        if (result.error || !result.data) {
            setFormError(
                result.error?.message ?? 'Could not save the meeting.',
            );
            return;
        }

        onSubmitted?.(result.data.id);
        resetAndClose();
    };

    if (!isOpen) return null;

    const nowInZone = nowWallTimeInZone(timeZone);

    const body = (
        <Form
            form={form}
            layout='vertical'
            requiredMark
            initialValues={initialValues}
            onFinish={handleSubmit}
            preserve={false}
        >
            {!isEdit && !issueKey && (
                <Form.Item
                    label='Issue'
                    name='issueKey'
                    rules={[
                        {
                            required: true,
                            message: `Select an issue in ${effectiveProjectKey}.`,
                        },
                        {
                            validator: (_rule, value: string | undefined) =>
                                !value
                                || !effectiveProjectKey
                                || value
                                    .trim()
                                    .toUpperCase()
                                    .startsWith(`${effectiveProjectKey}-`)
                                    ? Promise.resolve()
                                    : Promise.reject(
                                          new Error(
                                              `Issue must belong to project ${effectiveProjectKey}.`,
                                          ),
                                      ),
                        },
                    ]}
                >
                    <IssueField projectKey={effectiveProjectKey} />
                </Form.Item>
            )}
            <Form.Item
                label='Title'
                name='title'
                rules={[{ required: true, message: 'Enter a meeting title.' }]}
            >
                <Input
                    autoFocus={Boolean(isEdit || issueKey)}
                    placeholder='e.g. Sprint planning sync'
                />
            </Form.Item>
            <Form.Item
                label='Start date'
                name='startDate'
                rules={[{ required: true, message: 'Choose a start date.' }]}
            >
                <Input type='date' min={nowInZone.date} />
            </Form.Item>
            <Form.Item
                label='Start time'
                name='startTime'
                rules={[{ required: true, message: 'Choose a start time.' }]}
            >
                <Input type='time' />
            </Form.Item>
            <Form.Item label='Time zone'>
                <Select
                    showSearch
                    aria-label='Time zone'
                    value={timeZone}
                    options={TIME_ZONE_OPTIONS}
                    onChange={setTimeZone}
                />
            </Form.Item>
            {!isEdit && (
                <Form.Item label='Invitees'>
                    <WorkspaceUserPicker
                        value={invitees}
                        onChange={setInvitees}
                    />
                </Form.Item>
            )}
            {!isEdit && <AdvancedMeetingSettingsFields />}
            <Form.Item label='Description' name='description'>
                <Input.TextArea
                    rows={4}
                    placeholder='Add context or an agenda…'
                />
            </Form.Item>
            {formError && (
                <Form.Item>
                    <Alert type='error' message={formError} showIcon />
                </Form.Item>
            )}
        </Form>
    );

    const isSaving =
        scheduleMeeting.isPending
        || updateMeeting.isPending
        || (isEdit && editDetail.loading);

    return (
        <Modal
            title={isEdit ? 'Edit meeting' : 'Schedule a meeting'}
            chrome={chrome}
            onClose={resetAndClose}
            footer={
                <>
                    <Button variant='secondary' onClick={resetAndClose}>
                        Cancel
                    </Button>
                    <Button
                        variant='primary'
                        isLoading={isSaving}
                        onClick={() => form.submit()}
                    >
                        {isEdit ? 'Save changes' : 'Schedule meeting'}
                    </Button>
                </>
            }
        >
            {body}
        </Modal>
    );
}
