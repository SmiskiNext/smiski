import { Alert } from 'antd';
import type { ReactNode } from 'react';
import { Button, Modal } from '../ui';

export interface ConfirmActionDialogProps {
    title: string;
    message: ReactNode;
    confirmLabel: string;
    isLoading?: boolean;
    /** Backend rejection message from the last confirm attempt, if any. */
    error?: string | null;
    onConfirm: () => void;
    onClose: () => void;
    /** Pass 'embedded' when already rendered inside a Forge platform Modal. */
    chrome?: 'overlay' | 'embedded';
}

/**
 * Shared "are you sure?" confirmation for destructive meeting actions
 * (cancel/end). Surfaces the mutation's own error inline and stays open on
 * failure so the host can read what went wrong and retry, instead of the
 * action silently no-op'ing.
 */
export function ConfirmActionDialog({
    title,
    message,
    confirmLabel,
    isLoading,
    error,
    onConfirm,
    onClose,
    chrome = 'overlay',
}: ConfirmActionDialogProps) {
    return (
        <Modal
            title={title}
            chrome={chrome}
            onClose={onClose}
            size='sm'
            footer={
                <>
                    <Button
                        variant='ghost'
                        onClick={onClose}
                        disabled={isLoading}
                    >
                        Go back
                    </Button>
                    <Button
                        variant='danger'
                        isLoading={isLoading}
                        onClick={onConfirm}
                    >
                        {confirmLabel}
                    </Button>
                </>
            }
        >
            <div className='text-sm text-[var(--text-muted)]'>{message}</div>
            {error && (
                <Alert className='mt-3' type='error' message={error} showIcon />
            )}
        </Modal>
    );
}
