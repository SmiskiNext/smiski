/**
 * ScheduleMeetingModal — the shared schedule-meeting form. Chrome (backdrop,
 * card, header, footer) comes from the local `ui/Modal`; fields are still
 * Ant Design (`Form`, `Input`, `Select`, `Alert`) — see that component's
 * `chrome` doc comment for why a bare antd `Modal` isn't used here: its own
 * mask/backdrop rendered a stray dim layer when embedded inside a Forge
 * platform modal, since that modal already supplies the backdrop.
 *
 * The CREATE branch calls the real `meet` backend through Forge Remote
 * (`useScheduleMeeting`), capturing the backend's create settings plus
 * invitees carrying full identity from `WorkspaceUserPicker`. New meetings
 * default to a one-hour slot. The EDIT branch fetches full detail once, then
 * lets the host edit every field accepted by the backend's full-replace
 * operation: title, description, issue link, start/end, and timezone.
 */
import { Alert, Form, Input, Select } from 'antd';
import { useEffect, useRef, useState } from 'react';
import type { CreateMeetingSettingsInput } from '../../api/meetings';
import { listAllMeetings } from '../../api/meetings';
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
    isoToWallTimeInZone,
    listTimeZones,
    nowWallTimeInZone,
    resolveUserTimeZone,
    zonedWallTimeToIso,
} from '../../utils/datetime';
import { Button, Modal } from '../ui';
import { AdvancedMeetingSettingsFields } from './AdvancedMeetingSettingsFields';
import { IssuePicker } from './IssuePicker';
import {
    MEETING_TITLE_MAX_LENGTH,
    meetingDescriptionError,
    meetingEmailError,
    meetingTimeRangeError,
    meetingTitleError,
} from './meetingFormValidation';
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
    chatEnabled: true,
    allowMicrophone: true,
    allowVideo: true,
};

export interface ScheduleMeetingModalProps {
    isOpen: boolean;
    issueKey?: string;
    /** Numeric Jira issue identifier required by the backend issue-link contract. */
    issueId?: string;
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
    endDate: string;
    endTime: string;
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
    onIssueIdChange,
}: {
    projectKey: string;
    value?: string;
    onChange?: (issueKey: string) => void;
    onIssueIdChange?: (issueId?: string) => void;
}) {
    return (
        <IssuePicker
            projectKey={projectKey}
            value={value ?? ''}
            autoFocus
            onChange={(key, issue) => {
                onChange?.(key);
                onIssueIdChange?.(issue?.id);
            }}
        />
    );
}

/**
 * Whether the invoking user already has another `SCHEDULED` meeting starting
 * at the same instant, scoped to meetings *this user scheduled* (not every
 * meeting in the project) — the business rule only guards against one person
 * double-booking their own calendar.
 *
 * The check is advisory: an unreachable lookup reports "no conflict" so a
 * backend failure cannot block the form.
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
    const ownMeetings = await listAllMeetings({
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
    issueId,
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
    // List summaries omit endTime/zoneId, so edit initialization needs detail.
    const editDetail = useMeeting(isEdit ? meeting?.id : undefined);
    // Issue-context modal payloads normally include the project key, but derive
    // it from the linked issue as a defensive fallback so the picker/invitees
    // are never disabled merely because the optional context field was absent.
    const effectiveProjectKey =
        projectKey || meeting?.projectKey || issueKey?.split('-')[0] || '';
    const [invitees, setInvitees] = useState<WorkspaceUser[]>([]);
    const defaultTimeZone = resolveUserTimeZone(currentUser.timeZone);
    const [timeZone, setTimeZone] = useState(defaultTimeZone);
    const [selectedIssueId, setSelectedIssueId] = useState(issueId);
    const [formError, setFormError] = useState<string | null>(null);
    const initializedEditId = useRef<string>();

    const start = isoToWallTimeInZone(meeting?.scheduledAt, timeZone);
    const initialValues: Partial<ScheduleMeetingFormValues> = {
        issueKey: meeting?.issueKey,
        title: meeting?.title ?? '',
        description: meeting?.description ?? '',
        startDate: start.date,
        startTime: start.time,
        ...(isEdit ? {} : DEFAULT_ADVANCED_SETTINGS),
    };

    useEffect(() => {
        const detail = editDetail.meeting;
        if (!isOpen || !isEdit || !detail) return;
        if (initializedEditId.current === detail.id) return;

        const detailTimeZone = resolveUserTimeZone(detail.zoneId);
        const detailStart = isoToWallTimeInZone(
            detail.scheduledAt,
            detailTimeZone,
        );
        const fallbackEnd = detail.scheduledAt
            ? new Date(
                  new Date(detail.scheduledAt).getTime()
                      + DEFAULT_MEETING_DURATION_MS,
              ).toISOString()
            : undefined;
        const detailEnd = isoToWallTimeInZone(
            detail.endTime ?? fallbackEnd,
            detailTimeZone,
        );

        setTimeZone(detailTimeZone);
        setSelectedIssueId(detail.issueId);
        form.setFieldsValue({
            issueKey: detail.issueKey,
            title: detail.title,
            description: detail.description ?? '',
            startDate: detailStart.date,
            startTime: detailStart.time,
            endDate: detailEnd.date,
            endTime: detailEnd.time,
        });
        initializedEditId.current = detail.id;
    }, [editDetail.meeting, form, isEdit, isOpen]);

    const resetAndClose = () => {
        form.resetFields();
        setInvitees([]);
        setSelectedIssueId(issueId);
        setTimeZone(defaultTimeZone);
        setFormError(null);
        initializedEditId.current = undefined;
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
        const endIso = isEdit
            ? zonedWallTimeToIso(values.endDate, values.endTime, timeZone)
            : new Date(
                  new Date(startIso).getTime() + DEFAULT_MEETING_DURATION_MS,
              ).toISOString();
        const timeRangeError = meetingTimeRangeError(startIso, endIso);
        if (timeRangeError) {
            setFormError(timeRangeError);
            return;
        }

        const resolvedIssueKey =
            (isEdit ? values.issueKey : (issueKey ?? values.issueKey))
                ?.trim()
                .toUpperCase() ?? '';
        if (isEdit && !editDetail.meeting) {
            setFormError(
                'Meeting details are still loading — try again in a moment.',
            );
            return;
        }
        const resolvedIssueId = isEdit
            ? resolvedIssueKey === editDetail.meeting?.issueKey
                ? editDetail.meeting.issueId
                : selectedIssueId
            : (issueId ?? selectedIssueId);
        if (!resolvedIssueId) {
            setFormError('Select a valid Jira issue and try again.');
            return;
        }
        if (!isEdit) {
            const emailError = meetingEmailError(currentUser.email, invitees);
            if (emailError) {
                setFormError(emailError);
                return;
            }
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
            try {
                const updated = await updateMeeting.mutateAsync({
                    meetingId: meeting.id,
                    input: {
                        title,
                        description,
                        issueId: resolvedIssueId,
                        issueKey: resolvedIssueKey,
                        projectKey: effectiveProjectKey,
                        startTime: startIso,
                        endTime: endIso,
                        zoneId: timeZone,
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

        const result = await scheduleMeeting.mutateAsync({
            issueKey: resolvedIssueKey,
            issueId: resolvedIssueId,
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
                chatEnabled:
                    values.chatEnabled ?? DEFAULT_ADVANCED_SETTINGS.chatEnabled,
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
    const timeZoneOptions = TIME_ZONE_OPTIONS.some(
        (option) => option.value === timeZone,
    )
        ? TIME_ZONE_OPTIONS
        : [
              { value: timeZone, label: formatTimeZoneOption(timeZone) },
              ...TIME_ZONE_OPTIONS,
          ];

    const body = (
        <Form
            form={form}
            layout='vertical'
            requiredMark
            initialValues={initialValues}
            onFinish={handleSubmit}
            preserve={false}
        >
            {(isEdit || !issueKey) && (
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
                    <IssueField
                        projectKey={effectiveProjectKey}
                        onIssueIdChange={setSelectedIssueId}
                    />
                </Form.Item>
            )}
            <Form.Item
                label='Title'
                name='title'
                rules={[
                    {
                        validator: (_rule, value: string | undefined) => {
                            const error = meetingTitleError(value);
                            return error
                                ? Promise.reject(new Error(error))
                                : Promise.resolve();
                        },
                    },
                ]}
            >
                <Input
                    maxLength={MEETING_TITLE_MAX_LENGTH}
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
            {isEdit && (
                <>
                    <Form.Item
                        label='End date'
                        name='endDate'
                        rules={[
                            {
                                required: true,
                                message: 'Choose an end date.',
                            },
                        ]}
                    >
                        <Input type='date' min={nowInZone.date} />
                    </Form.Item>
                    <Form.Item
                        label='End time'
                        name='endTime'
                        rules={[
                            {
                                required: true,
                                message: 'Choose an end time.',
                            },
                        ]}
                    >
                        <Input type='time' />
                    </Form.Item>
                </>
            )}
            <Form.Item label='Time zone'>
                <Select
                    showSearch
                    aria-label='Time zone'
                    value={timeZone}
                    options={timeZoneOptions}
                    onChange={setTimeZone}
                />
            </Form.Item>
            {!isEdit && (
                <Form.Item label='Invitees'>
                    <WorkspaceUserPicker
                        value={invitees}
                        onChange={setInvitees}
                        requireEmail={true}
                    />
                </Form.Item>
            )}
            {!isEdit && <AdvancedMeetingSettingsFields />}
            <Form.Item
                label='Description'
                name='description'
                rules={[
                    {
                        validator: (_rule, value: string | undefined) => {
                            const error = meetingDescriptionError(value);
                            return error
                                ? Promise.reject(new Error(error))
                                : Promise.resolve();
                        },
                    },
                ]}
            >
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
