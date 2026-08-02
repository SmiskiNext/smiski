/**
 * MeetingSettingsModal — host-only meeting room settings (admission policy,
 * capacity, media toggles). Chrome/fields follow the same pattern as
 * `ScheduleMeetingModal`: local `ui/Modal` chrome, Ant Design `Form` fields.
 *
 * Backed by the real backend `updateSettings` operation
 * (`useUpdateMeetingSettings`), which the backend enforces as host-only and
 * rejects on COMPLETED/CANCELED meetings — callers must only render this for
 * a host on a SCHEDULED/RUNNING meeting (see `domain/meetingPolicy.ts`'s
 * `SETTINGS` action gating). `meeting.settings` is only present on a detail
 * (`get`) response, so this always fetches fresh detail via `useMeeting`
 * rather than trusting a possibly list-sourced `Meeting` prop.
 *
 * Field bounds mirror the backend's own validation exactly
 * (`UpdateMeetingSettingsRequest`): `admissionPolicy` is one of
 * `ALLOW_ALL`/`MANUAL_APPROVAL` (Jakarta `@Pattern`), `maxParticipants` is
 * 2-100 (Jakarta `@Min`/`@Max`, also enforced in the `MeetingSettings`
 * domain value object) — sending anything else 400s.
 */
import type { MeetUpdateMeetingSettingsRequest } from '@smiskinext/smiski-ts';
import { Alert, Form, InputNumber, Select, Switch } from 'antd';
import { useEffect, useState } from 'react';
import { useMeeting } from '../../hooks/useMeeting';
import { useUpdateMeetingSettings } from '../../hooks/useMeetingMutations';
import { Button, Modal } from '../ui';
import { LoadingState } from './LoadingState';

export interface MeetingSettingsModalProps {
    isOpen: boolean;
    meetingId: string;
    onClose: () => void;
    onSaved?: () => void;
    /** Pass 'embedded' when already rendered inside a Forge platform Modal. */
    chrome?: 'overlay' | 'embedded';
}

interface SettingsFormValues {
    admissionPolicy: 'ALLOW_ALL' | 'MANUAL_APPROVAL';
    maxParticipants: number;
    allowScreenShare: boolean;
    chatEnabled: boolean;
    allowMicrophone: boolean;
    allowVideo: boolean;
}

const ADMISSION_POLICY_OPTIONS: {
    value: SettingsFormValues['admissionPolicy'];
    label: string;
}[] = [
    { value: 'ALLOW_ALL', label: 'Anyone can join' },
    { value: 'MANUAL_APPROVAL', label: 'Host must approve each join request' },
];

export function MeetingSettingsModal({
    isOpen,
    meetingId,
    onClose,
    onSaved,
    chrome = 'overlay',
}: MeetingSettingsModalProps) {
    const [form] = Form.useForm<SettingsFormValues>();
    const detail = useMeeting(isOpen ? meetingId : undefined);
    const updateSettings = useUpdateMeetingSettings();
    const [formError, setFormError] = useState<string | null>(null);
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
        const request: MeetUpdateMeetingSettingsRequest = {
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

    const isLoadingDetail = detail.loading || !settings;

    return (
        <Modal
            title='Meeting settings'
            description='Only the host can change these.'
            chrome={chrome}
            onClose={resetAndClose}
            footer={
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
            }
        >
            {isLoadingDetail ? (
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
                            <Alert type='error' message={formError} showIcon />
                        </Form.Item>
                    )}
                </Form>
            )}
        </Modal>
    );
}
