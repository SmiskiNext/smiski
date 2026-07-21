/**
 * InlineFeedback — transient success/error feedback after a mock mutation
 * (start/schedule/cancel/record).
 * Auto-clears after a short delay so it reads like a toast without pulling in
 * a separate notification system.
 */
import { useEffect } from 'react';
import { Button, Icon, cn } from '../ui';

export interface InlineFeedbackProps {
  appearance: 'success' | 'error' | 'information';
  message: string;
  onDismiss: () => void;
  autoDismissMs?: number;
}

export function InlineFeedback({
  appearance,
  message,
  onDismiss,
  autoDismissMs = 4000,
}: InlineFeedbackProps) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, autoDismissMs);
    return () => clearTimeout(timer);
  }, [onDismiss, autoDismissMs]);

  const styles = {
    success:
      'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-200',
    error:
      'border-red-200 bg-red-50 text-red-800 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-200',
    information:
      'border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900/60 dark:bg-blue-950/30 dark:text-blue-200',
  };
  return (
    <div
      role="status"
      className={cn(
        'my-3 flex items-center gap-3 rounded-xl border px-3 py-2.5 text-sm',
        styles[appearance],
      )}
    >
      <Icon
        name={appearance === 'success' ? 'check' : appearance === 'error' ? 'alert' : 'info'}
        size={17}
      />
      <span className="min-w-0 flex-1">{message}</span>
      <Button
        variant="ghost"
        size="icon"
        className="size-7 min-h-0 text-current"
        aria-label="Dismiss"
        onClick={onDismiss}
      >
        <Icon name="x" size={15} />
      </Button>
    </div>
  );
}
