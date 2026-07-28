/**
 * ScheduleMeetingModal — the shared schedule-meeting form, built entirely with
 * Ant Design (`Modal`, `Form`, `Input`, `Select`, `Alert`). Used by the
 * project-page dashboard, the Issue Panel, and the embedded Forge modal root.
 *
 * The CREATE branch calls the real `meet` backend through Forge Remote
 * (`useScheduleMeeting`), capturing a start time, an end time, and a time zone
 * (seeded from the invoking user's Jira profile), plus invitees carrying full
 * identity from `WorkspaceUserPicker`. The EDIT branch stays on the in-memory
 * mock (`useUpdateMeeting`) and keeps its prior title/description/start-time
 * behavior. Backend failures are shown inline and keep the modal open.
 */
import { Alert, Form, Input, Modal, Select } from 'antd';
import { useState } from 'react';
import type { WorkspaceUser } from '../../api/workspaceUsers';
import { useCurrentUser } from '../../context/CurrentUserContext';
import type { Meeting } from '../../domain';
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
import { IssuePicker } from './IssuePicker';
import { WorkspaceUserPicker } from './WorkspaceUserPicker';

const TIME_ZONE_OPTIONS = listTimeZones().map((zone) => ({
    value: zone,
    label: formatTimeZoneOption(zone),
}));

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

interface ScheduleMeetingFormValues {
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

        try {
            if (isEdit && meeting) {
                const result = await updateMeeting.mutateAsync({
                    meetingId: meeting.id,
                    input: { title, description, startTime: startIso },
                });
                onSubmitted?.(result.id);
                resetAndClose();
                return;
            }

            const endIso = zonedWallTimeToIso(
                values.endDate,
                values.endTime,
                timeZone,
            );
            if (!endIso) {
                setFormError('Choose a valid end date and time.');
                return;
            }
            if (new Date(endIso).getTime() <= new Date(startIso).getTime()) {
                setFormError('The end time must be after the start time.');
                return;
            }

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
            });
            onSubmitted?.(result.id);
            resetAndClose();
        } catch (error) {
            setFormError(
                error instanceof Error
                    ? error.message
                    : 'Could not save the meeting.',
            );
        }
    };

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
            {!isEdit && (
                <>
                    <Form.Item
                        label='End date'
                        name='endDate'
                        rules={[
                            { required: true, message: 'Choose an end date.' },
                        ]}
                    >
                        <Input type='date' min={nowInZone.date} />
                    </Form.Item>
                    <Form.Item
                        label='End time'
                        name='endTime'
                        rules={[
                            { required: true, message: 'Choose an end time.' },
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

    return (
        <Modal
            title={isEdit ? 'Edit meeting' : 'Schedule a meeting'}
            open={isOpen}
            onCancel={resetAndClose}
            onOk={() => form.submit()}
            okText={isEdit ? 'Save changes' : 'Schedule meeting'}
            confirmLoading={
                scheduleMeeting.isPending || updateMeeting.isPending
            }
            destroyOnClose
            getContainer={chrome === 'embedded' ? false : undefined}
        >
            {body}
        </Modal>
    );
}
