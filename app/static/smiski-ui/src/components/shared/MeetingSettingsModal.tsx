/**
 * MeetingSettingsModal — the meeting room's settings dialog. Chrome/fields
 * follow the same pattern as `ScheduleMeetingModal`: local `ui/Modal` chrome,
 * Ant Design `Form` fields.
 *
 * Two independent sections, each with its own audience and its own persistence:
 *
 * - Room settings (admission policy, capacity, media toggles, chat) is
 *   **host-only** and backed by the real backend `updateSettings` operation
 *   (`useUpdateMeetingSettings`), which the backend also enforces as host-only
 *   and rejects on COMPLETED/CANCELED meetings — callers must only render this
 *   for a host on a SCHEDULED/RUNNING meeting (see `domain/meetingPolicy.ts`'s
 *   meeting action gating). `meeting.settings` is only present on a detail
 *   (`get`) response, so this fetches fresh detail via `useMeeting` rather than
 *   trusting a possibly list-sourced `Meeting` prop — and only when the host
 *   section is actually shown, so a non-host never requests settings they
 *   cannot see.
 * - Notifications is a **personal** preference owned by the caller (see
 *   `useParticipantPresenceNotifications`), applied the moment it is flipped
 *   and never routed through the form or the Save button. Opt in with
 *   `showNotificationPreferences`; only the meeting room does.
 *
 * The footer follows from that split: Cancel + Save belong to the host form, so
 * a viewer who only sees Notifications gets a single 'Done' action instead —
 * named to stay distinct from the header's own 'Close' button.
 *
 * Field bounds mirror the backend's own validation exactly
 * (`UpdateMeetingSettingsRequest`): `admissionPolicy` is one of
 * `ALLOW_ALL`/`MANUAL_APPROVAL` (Jakarta `@Pattern`), `maxParticipants` is
 * 2-100 (Jakarta `@Min`/`@Max`, also enforced in the `MeetingSettings`
 * domain value object) — sending anything else 400s.
 */
import { Alert, Form, InputNumber, Select, Switch } from 'antd';
import { useEffect, useId, useState } from 'react';
import type { MeetingSettings } from '../../domain';
import { useMeeting } from '../../hooks/useMeeting';
import { useUpdateMeetingSettings } from '../../hooks/useMeetingMutations';
import { Button, Icon, Modal } from '../ui';
import { ADMISSION_POLICY_OPTIONS } from './AdvancedMeetingSettingsFields';
import { LoadingState } from './LoadingState';
import { SectionHeading } from './SectionHeading';

export interface MeetingSettingsModalProps {
    isOpen: boolean;
    meetingId: string;
    onClose: () => void;
    onSaved?: () => void;
    /** Pass 'embedded' when already rendered inside a Forge platform Modal. */
    chrome?: 'overlay' | 'embedded';
    /**
     * Shows the personal join/leave notification toggle. Meeting-room only —
     * the toasts it controls exist nowhere else.
     */
    showNotificationPreferences?: boolean;
    /**
     * Gates the host-only room settings section *and* its detail fetch.
     * Defaults to `true` so existing host-only call sites are unaffected.
     */
    isHost?: boolean;
    /** Current state of the caller's join/leave notification preference. */
    notificationsEnabled?: boolean;
    /** Applied and persisted by the caller as soon as the toggle flips. */
    onNotificationsEnabledChange?: (enabled: boolean) => void;
}

interface SettingsFormValues {
    admissionPolicy: 'ALLOW_ALL' | 'MANUAL_APPROVAL';
    maxParticipants: number;
    allowScreenShare: boolean;
    chatEnabled: boolean;
    allowMicrophone: boolean;
    allowVideo: boolean;
}

export function MeetingSettingsModal({
    isOpen,
    meetingId,
    onClose,
    onSaved,
    chrome = 'overlay',
    showNotificationPreferences = false,
    isHost = true,
    notificationsEnabled = true,
    onNotificationsEnabledChange,
}: MeetingSettingsModalProps) {
    const [form] = Form.useForm<SettingsFormValues>();
    const detail = useMeeting(isOpen && isHost ? meetingId : undefined);
    const updateSettings = useUpdateMeetingSettings();
    const [formError, setFormError] = useState<string | null>(null);
    const notificationsToggleId = useId();
    const settings = detail.meeting?.settings;

    useEffect(() => {
        if (!settings) return;
        form.setFieldsValue({
            admissionPolicy:
                settings.admissionPolicy as SettingsFormValues['admissionPolicy'],
            maxParticipants: settings.maxParticipants,
            allowScreenShare: settings.allowScreenShare,
            chatEnabled: settings.chatEnabled,
            allowMicrophone: settings.allowMicrophone,
            allowVideo: settings.allowVideo,
        });
    }, [settings, form]);

    const resetAndClose = () => {
        form.resetFields();
        setFormError(null);
        onClose();
    };

    const handleSubmit = async (values: SettingsFormValues) => {
        setFormError(null);
        const request: MeetingSettings = {
            admissionPolicy: values.admissionPolicy,
            maxParticipants: values.maxParticipants,
            allowScreenShare: values.allowScreenShare,
            chatEnabled: values.chatEnabled,
            allowMicrophone: values.allowMicrophone,
            allowVideo: values.allowVideo,
        };
        try {
            await updateSettings.mutateAsync({ meetingId, settings: request });
            onSaved?.();
            resetAndClose();
        } catch (error) {
            setFormError(
                error instanceof Error
                    ? error.message
                    : 'Could not save meeting settings.',
            );
        }
    };

    if (!isOpen) return null;

    const showRoomSection = isHost;
    const showBothSections = showNotificationPreferences && showRoomSection;
    const isLoadingDetail = showRoomSection && (detail.loading || !settings);

    return (
        <Modal
            title='Meeting settings'
            description={
                showRoomSection ? 'Only the host can change these.' : undefined
            }
            chrome={chrome}
            onClose={resetAndClose}
            footer={
                showRoomSection ? (
                    <>
                        <Button variant='secondary' onClick={resetAndClose}>
                            Cancel
                        </Button>
                        <Button
                            variant='primary'
                            isLoading={updateSettings.isPending}
                            disabled={isLoadingDetail}
                            onClick={() => form.submit()}
                        >
                            Save settings
                        </Button>
                    </>
                ) : (
                    <Button variant='secondary' onClick={resetAndClose}>
                        Done
                    </Button>
                )
            }
        >
            {showNotificationPreferences && (
                <section>
                    <SectionHeading>
                        <span className='inline-flex items-center gap-1.5'>
                            <Icon
                                name={notificationsEnabled ? 'bell' : 'bellOff'}
                                size={14}
                            />
                            Notifications
                        </span>
                    </SectionHeading>
                    <div className='flex items-center justify-between gap-3 rounded-lg border border-[var(--border)] px-3.5 py-3'>
                        <div>
                            <label
                                htmlFor={notificationsToggleId}
                                className='block text-sm font-medium text-[var(--text)]'
                            >
                                Join and leave notifications
                            </label>
                            <p className='text-xs text-[var(--text-muted)]'>
                                Show a toast when someone joins or leaves this
                                meeting.
                            </p>
                        </div>
                        <Switch
                            id={notificationsToggleId}
                            checked={notificationsEnabled}
                            onChange={onNotificationsEnabledChange}
                        />
                    </div>
                </section>
            )}
            {showBothSections && <SectionHeading>Room settings</SectionHeading>}
            {showRoomSection
                && (isLoadingDetail ? (
                    <LoadingState label='Loading settings…' />
                ) : (
                    <Form
                        form={form}
                        layout='vertical'
                        requiredMark
                        onFinish={handleSubmit}
                    >
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
                        {formError && (
                            <Form.Item>
                                <Alert
                                    type='error'
                                    message={formError}
                                    showIcon
                                />
                            </Form.Item>
                        )}
                    </Form>
                ))}
        </Modal>
    );
}
