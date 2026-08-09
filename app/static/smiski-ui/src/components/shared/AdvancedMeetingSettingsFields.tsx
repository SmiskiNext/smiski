import { Collapse, Form, InputNumber, Select, Switch } from 'antd';

export const ADMISSION_POLICY_OPTIONS: {
    value: 'ALLOW_ALL' | 'MANUAL_APPROVAL';
    label: string;
}[] = [
    { value: 'ALLOW_ALL', label: 'Anyone can join' },
    { value: 'MANUAL_APPROVAL', label: 'Host must approve each join request' },
];

/**
 * Collapsed-by-default "Advanced settings" section for the instant/schedule
 * create forms. Must be rendered inside an antd `Form` whose field names for
 * `admissionPolicy`/`maxParticipants`/`allowScreenShare`/`allowMicrophone`/
 * `chatEnabled`/`allowVideo` match `DEFAULT_MEETING_SETTINGS` in
 * `api/meetings.ts` — the
 * bounds here mirror the backend's own validation exactly
 * (`UpdateMeetingSettingsRequest`/`MeetingSettings`), same as
 * `MeetingSettingsModal`'s fields for an existing meeting.
 */
export function AdvancedMeetingSettingsFields() {
    return (
        <Collapse
            ghost
            items={[
                {
                    key: 'advanced-settings',
                    label: 'Advanced settings',
                    children: (
                        <>
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
                        </>
                    ),
                },
            ]}
        />
    );
}
