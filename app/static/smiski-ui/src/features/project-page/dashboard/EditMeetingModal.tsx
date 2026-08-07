/**
 * EditMeetingModal — unified edit modal for SCHEDULED meetings on project page.
 * Combines meeting info (title/description/time), settings (admission/capacity/
 * media), and invitees (add/remove) into a single scrollable form with one
 * "Save changes" button.
 *
 * The backend operations remain separate (updateMeeting, updateMeetingSettings,
 * add/removeMeetingInvitees), but the orchestration is sequential: Info →
 * Settings → Invitees. On partial error, the modal stays open, reports which
 * section failed, and the user can retry — invitee diff is recalculated against
 * the latest cache on retry, so no duplicate adds.
 *
 * This modal is project-page-specific and replaces the separate flows:
 * - ScheduleMeetingModal (edit mode) for info
 * - MeetingSettingsModal for settings
 * - MeetingInviteeManager for invitees
 *
 * Issue panel remains unchanged (still uses those separate components).
 */
import { Alert, Form, Input, InputNumber, Select, Switch } from 'antd';
import { useEffect, useState } from 'react';
import type { WorkspaceUser } from '../../../api/workspaceUsers';
import { ADMISSION_POLICY_OPTIONS } from '../../../components/shared/AdvancedMeetingSettingsFields';
import { WorkspaceUserPicker } from '../../../components/shared/WorkspaceUserPicker';
import { Button, Modal } from '../../../components/ui';
import type { MeetingSettings } from '../../../domain';
import { useMeeting } from '../../../hooks/useMeeting';
import {
    useAddMeetingInvitees,
    useMeetingInvitees,
    useRemoveMeetingInvitees,
} from '../../../hooks/useMeetingInvitees';
import {
    useUpdateMeeting,
    useUpdateMeetingSettings,
} from '../../../hooks/useMeetingMutations';
import {
    formatTimeZoneOption,
    listTimeZones,
    nowWallTimeInZone,
    zonedWallTimeToIso,
} from '../../../utils/datetime';

const TIME_ZONE_OPTIONS = listTimeZones().map((zone) => ({
    value: zone,
    label: formatTimeZoneOption(zone),
}));

export interface EditMeetingModalProps {
    isOpen: boolean;
    meetingId: string;
    onClose: () => void;
    onSaved?: () => void;
}

interface EditMeetingFormValues {
    title: string;
    description: string;
    startDate: string;
    startTime: string;
    admissionPolicy: 'ALLOW_ALL' | 'MANUAL_APPROVAL';
    maxParticipants: number;
    allowScreenShare: boolean;
    chatEnabled: boolean;
    allowMicrophone: boolean;
    allowVideo: boolean;
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

export function EditMeetingModal({
    isOpen,
    meetingId,
    onClose,
    onSaved,
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

    const isLoading = meetingLoading || inviteesLoading;
    const isSaving =
        updateMeeting.isPending
        || updateSettings.isPending
        || addInvitees.isPending
        || removeInvitees.isPending;

    useEffect(() => {
        if (!meeting?.settings) return;
        const start = wallTimeParts(meeting.scheduledAt);
        form.setFieldsValue({
            title: meeting.title,
            description: meeting.description ?? '',
            startDate: start.date,
            startTime: start.time,
            admissionPolicy: meeting.settings
                .admissionPolicy as EditMeetingFormValues['admissionPolicy'],
            maxParticipants: meeting.settings.maxParticipants,
            allowScreenShare: meeting.settings.allowScreenShare,
            chatEnabled: meeting.settings.chatEnabled,
            allowMicrophone: meeting.settings.allowMicrophone,
            allowVideo: meeting.settings.allowVideo,
        });
        setTimeZone(meeting.zoneId ?? 'UTC');
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

    const resetAndClose = () => {
        form.resetFields();
        setNewInvitees([]);
        setFormError(null);
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

        const infoDirty =
            title !== meeting.title
            || description !== (meeting.description ?? '')
            || startIso !== meeting.scheduledAt;

        const formSettings: MeetingSettings = {
            admissionPolicy: values.admissionPolicy,
            maxParticipants: values.maxParticipants,
            allowScreenShare: values.allowScreenShare,
            chatEnabled: values.chatEnabled,
            allowMicrophone: values.allowMicrophone,
            allowVideo: values.allowVideo,
        };
        const settingsDirty = !shallowEqualSettings(
            formSettings,
            meeting.settings,
        );

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
                    input: {
                        title,
                        description,
                        startTime: startIso,
                        detail: meeting,
                    },
                });
            }

            if (settingsDirty) {
                await updateSettings.mutateAsync({
                    meetingId,
                    settings: formSettings,
                });
            }

            if (toAdd.length > 0) {
                await addInvitees.mutateAsync({
                    meetingId,
                    invitees: toAdd.map((inv) => ({
                        accountId: inv.accountId,
                        displayName: inv.displayName,
                        email: inv.email,
                    })),
                });
            }

            if (toRemove.length > 0) {
                await removeInvitees.mutateAsync({
                    meetingId,
                    inviteeIds: toRemove,
                });
            }

            onSaved?.();
            resetAndClose();
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
                    <Form.Item
                        label='Start date'
                        name='startDate'
                        rules={[
                            { required: true, message: 'Choose a start date.' },
                        ]}
                    >
                        <Input type='date' min={nowInZone.date} />
                    </Form.Item>
                    <Form.Item
                        label='Start time'
                        name='startTime'
                        rules={[
                            { required: true, message: 'Choose a start time.' },
                        ]}
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
                    <Form.Item label='Description' name='description'>
                        <Input.TextArea
                            rows={3}
                            placeholder='Add context or an agenda…'
                        />
                    </Form.Item>

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
                                message: 'Enter a value between 2 and 100.',
                            },
                        ]}
                    >
                        <InputNumber min={2} max={100} className='w-full' />
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
