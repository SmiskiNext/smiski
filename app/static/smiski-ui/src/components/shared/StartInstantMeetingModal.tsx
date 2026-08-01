/**
 * StartInstantMeetingModal — the shared "start instant meeting" form. Chrome
 * (backdrop, card, header, footer) comes from the local `ui/Modal`; fields
 * are still Ant Design (`Form`, `Input`). Used by both the project-page
 * dashboard (overlay chrome) and the Issue Panel (embedded in a Forge
 * platform modal) — see `ui/Modal`'s `chrome` doc comment for why a bare
 * antd `Modal` isn't used: its own mask/backdrop rendered a stray dim layer
 * when embedded inside a Forge platform modal, since that modal already
 * supplies the backdrop.
 *
 * Invitees are sourced from Jira site users through `WorkspaceUserPicker` and
 * kept as full identity objects (`accountId`, `displayName`, `email`) so the
 * create request satisfies the backend contract. Backend/resolver failures are
 * shown inline and keep the modal open for correction.
 */
import { Alert, Form, Input } from 'antd';
import { useState } from 'react';
import type { WorkspaceUser } from '../../api/workspaceUsers';
import { useCurrentUser } from '../../context/CurrentUserContext';
import { useCreateInstantMeeting } from '../../hooks/useMeetingMutations';
import { resolveUserTimeZone } from '../../utils/datetime';
import { Button, Modal } from '../ui';
import { IssuePicker } from './IssuePicker';
import { WorkspaceUserPicker } from './WorkspaceUserPicker';

export interface StartInstantMeetingModalProps {
    isOpen: boolean;
    projectKey: string;
    /** When provided, the meeting binds to this issue and the picker is hidden. */
    issueKey?: string;
    onClose: () => void;
    onStarted: (meetingId: string) => void;
    /** Pass 'embedded' when already rendered inside a Forge platform Modal. */
    chrome?: 'overlay' | 'embedded';
}

interface InstantMeetingFormValues {
    issueKey?: string;
    title: string;
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

export function StartInstantMeetingModal({
    isOpen,
    projectKey,
    issueKey,
    onClose,
    onStarted,
    chrome = 'overlay',
}: StartInstantMeetingModalProps) {
    const [form] = Form.useForm<InstantMeetingFormValues>();
    const currentUser = useCurrentUser();
    const createMeeting = useCreateInstantMeeting();
    const [invitees, setInvitees] = useState<WorkspaceUser[]>([]);
    const [formError, setFormError] = useState<string | null>(null);

    const handleSubmit = async (values: InstantMeetingFormValues) => {
        setFormError(null);
        const resolvedIssueKey = (issueKey ?? values.issueKey ?? '')
            .trim()
            .toUpperCase();

        const result = await createMeeting.mutateAsync({
            issueKey: resolvedIssueKey,
            projectKey,
            title: values.title.trim(),
            zoneId: resolveUserTimeZone(currentUser.timeZone),
            invitees: invitees.map((user) => ({
                accountId: user.accountId,
                displayName: user.displayName,
                email: user.email,
            })),
            host: {
                accountId: currentUser.accountId,
                displayName: currentUser.displayName,
                email: currentUser.email,
                avatarUrl: currentUser.avatarUrl,
            },
        });

        if (result.error || !result.data) {
            setFormError(
                result.error?.message ?? 'Could not start the meeting.',
            );
            return;
        }

        const meetingId = result.data.id;
        resetAndClose();
        onStarted(meetingId);
    };

    const resetAndClose = () => {
        form.resetFields();
        setInvitees([]);
        setFormError(null);
        onClose();
    };

    const body = (
        <Form
            form={form}
            layout='vertical'
            requiredMark
            onFinish={handleSubmit}
            preserve={false}
        >
            {!issueKey && (
                <Form.Item
                    label='Issue'
                    name='issueKey'
                    rules={[
                        {
                            required: true,
                            message: `Select an issue in ${projectKey}.`,
                        },
                        {
                            validator: (_rule, value: string | undefined) =>
                                !value
                                || value
                                    .trim()
                                    .toUpperCase()
                                    .startsWith(`${projectKey}-`)
                                    ? Promise.resolve()
                                    : Promise.reject(
                                          new Error(
                                              `Issue must belong to project ${projectKey}.`,
                                          ),
                                      ),
                        },
                    ]}
                >
                    <IssueField projectKey={projectKey} />
                </Form.Item>
            )}
            <Form.Item
                label='Title'
                name='title'
                rules={[{ required: true, message: 'Enter a meeting title.' }]}
            >
                <Input
                    autoFocus={Boolean(issueKey)}
                    placeholder='e.g. Investigate deployment failure'
                />
            </Form.Item>
            <Form.Item label='Invitees'>
                <WorkspaceUserPicker value={invitees} onChange={setInvitees} />
            </Form.Item>
            {formError && (
                <Form.Item>
                    <Alert type='error' message={formError} showIcon />
                </Form.Item>
            )}
        </Form>
    );

    if (!isOpen) return null;

    return (
        <Modal
            title='Start instant meeting'
            chrome={chrome}
            onClose={resetAndClose}
            footer={
                <>
                    <Button variant='secondary' onClick={resetAndClose}>
                        Cancel
                    </Button>
                    <Button
                        variant='primary'
                        isLoading={createMeeting.isPending}
                        onClick={() => form.submit()}
                    >
                        Start meeting
                    </Button>
                </>
            }
        >
            {body}
        </Modal>
    );
}
