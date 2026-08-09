/**
 * EditMeetingModal — shared unified edit modal, available to the
 * host across all meeting statuses (SCHEDULED, RUNNING, COMPLETED, CANCELED).
 * Combines meeting info (title/description/issue/time), settings (admission/
 * capacity/media), and invitees (add/remove) into a single scrollable form with
 * one "Save changes" button.
 *
 * The backend operations remain separate (updateMeeting, updateMeetingSettings,
 * add/removeMeetingInvitees), but the orchestration is sequential: Info →
 * Settings → Invitees. On partial error, the modal stays open, reports which
 * section failed, and the user can retry — invitee diff is recalculated against
 * the latest cache on retry, so no duplicate adds.
 *
 * Status-aware behavior (matches backend gates):
 * - Title/description/issue link: editable in every status.
 * - Time/zone: editable only when status === SCHEDULED (disabled + grayed out
 *   otherwise); backend rejects changes in other statuses.
 * - Settings/invitees: hidden entirely when status is COMPLETED or CANCELED
 *   (backend rejects those operations on terminal meetings).
 *
 * This modal is shared by the project page and issue-panel Forge modal. It
 * replaces the separate flows:
 * - the former ScheduleMeetingModal edit mode for info
 * - MeetingSettingsModal for settings
 * - MeetingInviteeManager for invitees
 */
import { Alert, Form, Input, InputNumber, Select, Switch } from 'antd';
import { useEffect, useState } from 'react';
import type { WorkspaceUser } from '../../api/workspaceUsers';
import type { JiraIssue, MeetingSettings } from '../../domain';
import { useMeeting } from '../../hooks/useMeeting';
import {
    useAddMeetingInvitees,
    useMeetingInvitees,
    useRemoveMeetingInvitees,
} from '../../hooks/useMeetingInvitees';
import {
    useUpdateMeeting,
    useUpdateMeetingSettings,
} from '../../hooks/useMeetingMutations';
import { useProjectIssues } from '../../hooks/useProjectIssues';
import {
    formatTimeZoneOption,
    isoToWallTimeInZone,
    listTimeZones,
    nowWallTimeInZone,
    zonedWallTimeToIso,
} from '../../utils/datetime';
import { Button, Modal } from '../ui';
import { ADMISSION_POLICY_OPTIONS } from './AdvancedMeetingSettingsFields';
import { meetingTimeRangeError } from './meetingFormValidation';
import { WorkspaceUserPicker } from './WorkspaceUserPicker';

const DEFAULT_MEETING_DURATION_MS = 60 * 60 * 1000;

const TIME_ZONE_OPTIONS = listTimeZones().map((zone) => ({
    value: zone,
    label: formatTimeZoneOption(zone),
}));

export interface EditMeetingModalProps {
    isOpen: boolean;
    meetingId: string;
    onClose: () => void;
    onSaved?: () => void;
    /** Pass 'embedded' when Jira's Forge modal supplies the outer chrome. */
    chrome?: 'overlay' | 'embedded';
}

interface EditMeetingFormValues {
    title: string;
    description: string;
    startDate: string;
    startTime: string;
    endDate: string;
    endTime: string;
    admissionPolicy: 'ALLOW_ALL' | 'MANUAL_APPROVAL';
    maxParticipants: number;
    allowScreenShare: boolean;
    chatEnabled: boolean;
    allowMicrophone: boolean;
    allowVideo: boolean;
}

interface SelectedIssue {
    issueId: string;
    issueKey: string;
    projectKey: string;
}

function issueOptionLabel(issue: JiraIssue): string {
    return `${issue.key} — ${issue.summary}`;
}

function shallowEqualSettings(a: MeetingSettings, b: MeetingSettings): boolean {
    return (
        a.admissionPolicy === b.admissionPolicy
        && a.maxParticipants === b.maxParticipants
        && a.allowScreenShare === b.allowScreenShare
        && a.chatEnabled === b.chatEnabled
        && a.allowMicrophone === b.allowMicrophone
        && a.allowVideo === b.allowVideo
    );
}

function sameInstant(left?: string, right?: string): boolean {
    if (!left || !right) return left === right;
    const leftMs = new Date(left).getTime();
    const rightMs = new Date(right).getTime();
    return !Number.isNaN(leftMs) && leftMs === rightMs;
}

export function EditMeetingModal({
    isOpen,
    meetingId,
    onClose,
    onSaved,
    chrome = 'overlay',
}: EditMeetingModalProps) {
    const [form] = Form.useForm<EditMeetingFormValues>();
    const { meeting, loading: meetingLoading } = useMeeting(
        isOpen ? meetingId : undefined,
    );
    const { invitees, loading: inviteesLoading } = useMeetingInvitees(
        isOpen ? meetingId : undefined,
    );
    const updateMeeting = useUpdateMeeting();
    const updateSettings = useUpdateMeetingSettings();
    const addInvitees = useAddMeetingInvitees();
    const removeInvitees = useRemoveMeetingInvitees();

    const [timeZone, setTimeZone] = useState('UTC');
    const [newInvitees, setNewInvitees] = useState<WorkspaceUser[]>([]);
    const [formError, setFormError] = useState<string | null>(null);
    const [inviteesSeeded, setInviteesSeeded] = useState(false);
    const [selectedIssue, setSelectedIssue] = useState<SelectedIssue | null>(
        null,
    );
    const [issueQuery, setIssueQuery] = useState('');

    const canEditSchedule = meeting?.status === 'SCHEDULED';
    const canManageSettingsAndInvitees =
        meeting?.status === 'SCHEDULED' || meeting?.status === 'RUNNING';

    const { issues, loading: issuesLoading } = useProjectIssues(
        meeting?.projectKey ?? '',
        issueQuery,
        isOpen && Boolean(meeting?.projectKey),
    );

    const isLoading = meetingLoading || inviteesLoading;
    const isSaving =
        updateMeeting.isPending
        || updateSettings.isPending
        || addInvitees.isPending
        || removeInvitees.isPending;

    useEffect(() => {
        if (!meeting?.settings) return;
        const detailTimeZone = meeting.zoneId ?? 'UTC';
        const start = isoToWallTimeInZone(meeting.scheduledAt, detailTimeZone);
        const fallbackEnd = meeting.scheduledAt
            ? new Date(
                  new Date(meeting.scheduledAt).getTime()
                      + DEFAULT_MEETING_DURATION_MS,
              ).toISOString()
            : undefined;
        const end = isoToWallTimeInZone(
            meeting.endTime ?? fallbackEnd,
            detailTimeZone,
        );
        form.setFieldsValue({
            title: meeting.title,
            description: meeting.description ?? '',
            startDate: start.date,
            startTime: start.time,
            endDate: end.date,
            endTime: end.time,
            admissionPolicy: meeting.settings
                .admissionPolicy as EditMeetingFormValues['admissionPolicy'],
            maxParticipants: meeting.settings.maxParticipants,
            allowScreenShare: meeting.settings.allowScreenShare,
            chatEnabled: meeting.settings.chatEnabled,
            allowMicrophone: meeting.settings.allowMicrophone,
            allowVideo: meeting.settings.allowVideo,
        });
        setTimeZone(detailTimeZone);
        if (meeting.issueId && meeting.issueKey) {
            setSelectedIssue({
                issueId: meeting.issueId,
                issueKey: meeting.issueKey,
                projectKey: meeting.projectKey,
            });
        }
    }, [meeting, form]);

    // Seed invitees only on initial load (when meetingId changes), not on every
    // cache update — otherwise a partial failure (info/settings saved, invitees
    // errored) that refreshes the cache would silently discard the user's
    // unsaved invitee changes and make retry a no-op.
    useEffect(() => {
        if (inviteesSeeded || !invitees.length) return;
        setNewInvitees(
            invitees.map((inv) => ({
                accountId: inv.accountId,
                displayName: inv.displayName,
                email: inv.email,
            })),
        );
        setInviteesSeeded(true);
    }, [invitees, inviteesSeeded]);

    // biome-ignore lint/correctness/useExhaustiveDependencies: Reset state when meetingId changes
    useEffect(() => {
        setInviteesSeeded(false);
        setNewInvitees([]);
    }, [meetingId]);

    const resetState = () => {
        form.resetFields();
        setNewInvitees([]);
        setFormError(null);
        setSelectedIssue(null);
        setIssueQuery('');
    };

    const resetAndClose = () => {
        resetState();
        onClose();
    };

    const handleSubmit = async (values: EditMeetingFormValues) => {
        if (!meeting?.settings) {
            setFormError('Meeting details are not available.');
            return;
        }
        setFormError(null);

        const title = values.title.trim();
        const description = values.description?.trim() ?? '';

        let startIso: string | undefined;
        let endIso: string | undefined;
        if (canEditSchedule) {
            const computedIso = zonedWallTimeToIso(
                values.startDate,
                values.startTime,
                timeZone,
            );
            if (!computedIso) {
                setFormError('Choose a valid start date and time.');
                return;
            }
            if (new Date(computedIso).getTime() <= Date.now()) {
                setFormError('Choose a start date and time in the future.');
                return;
            }
            startIso = computedIso;
            endIso = zonedWallTimeToIso(
                values.endDate,
                values.endTime,
                timeZone,
            );
            const timeRangeError = meetingTimeRangeError(startIso, endIso);
            if (timeRangeError) {
                setFormError(timeRangeError);
                return;
            }
        }

        const effectiveIssue = selectedIssue ?? {
            issueId: meeting.issueId,
            issueKey: meeting.issueKey,
            projectKey: meeting.projectKey,
        };
        const issueChanged =
            selectedIssue !== null
            && (selectedIssue.issueId !== meeting.issueId
                || selectedIssue.issueKey !== meeting.issueKey);

        const startTimeChanged =
            canEditSchedule && !sameInstant(startIso, meeting.scheduledAt);
        const endTimeChanged =
            canEditSchedule && !sameInstant(endIso, meeting.endTime);
        const zoneChanged =
            canEditSchedule && timeZone !== (meeting.zoneId ?? 'UTC');

        const infoDirty =
            title !== meeting.title
            || description !== (meeting.description ?? '')
            || startTimeChanged
            || endTimeChanged
            || zoneChanged
            || issueChanged;

        const formSettings: MeetingSettings = {
            admissionPolicy: values.admissionPolicy,
            maxParticipants: values.maxParticipants,
            allowScreenShare: values.allowScreenShare,
            chatEnabled: values.chatEnabled,
            allowMicrophone: values.allowMicrophone,
            allowVideo: values.allowVideo,
        };
        const settingsDirty =
            canManageSettingsAndInvitees
            && !shallowEqualSettings(formSettings, meeting.settings);

        const currentIds = new Set(invitees.map((inv) => inv.accountId));
        const newIds = new Set(newInvitees.map((inv) => inv.accountId));
        const toAdd = newInvitees.filter(
            (inv) => !currentIds.has(inv.accountId),
        );
        const toRemove = invitees
            .filter((inv) => !newIds.has(inv.accountId))
            .map((inv) => inv.id);

        try {
            if (infoDirty) {
                await updateMeeting.mutateAsync({
                    meetingId,
                    input:
                        canEditSchedule && startIso && endIso
                            ? {
                                  title,
                                  description,
                                  ...effectiveIssue,
                                  startTime: startIso,
                                  endTime: endIso,
                                  zoneId: timeZone,
                              }
                            : {
                                  title,
                                  description,
                                  detail: meeting,
                                  selectedIssue: effectiveIssue,
                              },
                });
            }

            if (settingsDirty) {
                await updateSettings.mutateAsync({
                    meetingId,
                    settings: formSettings,
                });
            }

            if (canManageSettingsAndInvitees && toAdd.length > 0) {
                await addInvitees.mutateAsync({
                    meetingId,
                    invitees: toAdd.map((inv) => ({
                        accountId: inv.accountId,
                        displayName: inv.displayName,
                        email: inv.email,
                    })),
                });
            }

            if (canManageSettingsAndInvitees && toRemove.length > 0) {
                await removeInvitees.mutateAsync({
                    meetingId,
                    inviteeIds: toRemove,
                });
            }

            resetState();
            if (onSaved) onSaved();
            else onClose();
        } catch (error) {
            setFormError(
                error instanceof Error
                    ? error.message
                    : 'Could not save changes.',
            );
        }
    };

    if (!isOpen) return null;

    const nowInZone = nowWallTimeInZone(timeZone);

    return (
        <Modal
            title='Edit meeting'
            size='lg'
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
                        disabled={isLoading}
                        onClick={() => form.submit()}
                    >
                        Save changes
                    </Button>
                </>
            }
        >
            {isLoading ? (
                <div className='py-8 text-center text-sm text-[var(--text-muted)]'>
                    Loading meeting details…
                </div>
            ) : (
                <Form
                    form={form}
                    layout='vertical'
                    requiredMark
                    onFinish={handleSubmit}
                    preserve={false}
                >
                    <h3 className='mb-4 text-sm font-semibold text-[var(--text)]'>
                        Meeting information
                    </h3>
                    <Form.Item
                        label='Title'
                        name='title'
                        rules={[
                            {
                                required: true,
                                message: 'Enter a meeting title.',
                            },
                        ]}
                    >
                        <Input placeholder='e.g. Sprint planning sync' />
                    </Form.Item>
                    <Form.Item label='Linked issue' required>
                        <Select
                            showSearch
                            placeholder='Search for an issue…'
                            aria-label='Linked issue'
                            value={selectedIssue?.issueKey}
                            options={issues.map((issue) => ({
                                value: issue.key,
                                label: issueOptionLabel(issue),
                            }))}
                            onSearch={setIssueQuery}
                            onChange={(key) => {
                                const issue = issues.find((i) => i.key === key);
                                if (issue && meeting) {
                                    setSelectedIssue({
                                        issueId: issue.id,
                                        issueKey: issue.key,
                                        projectKey: meeting.projectKey,
                                    });
                                }
                            }}
                            loading={issuesLoading}
                            filterOption={false}
                            notFoundContent={
                                issuesLoading ? 'Loading…' : 'No issues found'
                            }
                        />
                    </Form.Item>
                    {canEditSchedule && (
                        <>
                            <Form.Item
                                label='Start date'
                                name='startDate'
                                rules={[
                                    {
                                        required: true,
                                        message: 'Choose a start date.',
                                    },
                                ]}
                            >
                                <Input type='date' min={nowInZone.date} />
                            </Form.Item>
                            <Form.Item
                                label='Start time'
                                name='startTime'
                                rules={[
                                    {
                                        required: true,
                                        message: 'Choose a start time.',
                                    },
                                ]}
                            >
                                <Input type='time' />
                            </Form.Item>
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
                            <Form.Item label='Time zone' required>
                                <Select
                                    showSearch
                                    aria-label='Time zone'
                                    value={timeZone}
                                    options={TIME_ZONE_OPTIONS}
                                    onChange={setTimeZone}
                                />
                            </Form.Item>
                        </>
                    )}
                    <Form.Item label='Description' name='description'>
                        <Input.TextArea
                            rows={3}
                            placeholder='Add context or an agenda…'
                        />
                    </Form.Item>

                    {canManageSettingsAndInvitees && (
                        <>
                            <h3 className='mb-4 mt-6 text-sm font-semibold text-[var(--text)]'>
                                Settings
                            </h3>
                            <Form.Item
                                label='Who can join'
                                name='admissionPolicy'
                                rules={[
                                    {
                                        required: true,
                                        message: 'Choose an admission policy.',
                                    },
                                ]}
                            >
                                <Select options={ADMISSION_POLICY_OPTIONS} />
                            </Form.Item>
                            <Form.Item
                                label='Max participants'
                                name='maxParticipants'
                                rules={[
                                    {
                                        required: true,
                                        message:
                                            'Enter a value between 2 and 100.',
                                    },
                                ]}
                            >
                                <InputNumber
                                    min={2}
                                    max={100}
                                    className='w-full'
                                />
                            </Form.Item>
                            <Form.Item
                                label='Allow screen share'
                                name='allowScreenShare'
                                valuePropName='checked'
                            >
                                <Switch />
                            </Form.Item>
                            <Form.Item
                                label='Enable chat'
                                name='chatEnabled'
                                valuePropName='checked'
                            >
                                <Switch />
                            </Form.Item>
                            <Form.Item
                                label='Allow microphone'
                                name='allowMicrophone'
                                valuePropName='checked'
                            >
                                <Switch />
                            </Form.Item>
                            <Form.Item
                                label='Allow video'
                                name='allowVideo'
                                valuePropName='checked'
                            >
                                <Switch />
                            </Form.Item>

                            <h3 className='mb-4 mt-6 text-sm font-semibold text-[var(--text)]'>
                                Invitees
                            </h3>
                            <Form.Item label='Manage invitees'>
                                <WorkspaceUserPicker
                                    value={newInvitees}
                                    onChange={setNewInvitees}
                                    requireEmail
                                />
                            </Form.Item>
                        </>
                    )}

                    {formError && (
                        <Form.Item>
                            <Alert type='error' message={formError} showIcon />
                        </Form.Item>
                    )}
                </Form>
            )}
        </Modal>
    );
}
