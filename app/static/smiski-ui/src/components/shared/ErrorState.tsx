/**
 * ErrorState — consistent inline error view for list/data views.
 */
import { Icon } from '../ui';

export interface ErrorStateProps {
  title?: string;
  message?: string;
}

export function ErrorState({ title = 'Something went wrong', message }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-red-800 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-200"
    >
      <Icon name="alert" className="mt-0.5 shrink-0" />
      <div>
        <p className="font-semibold">{title}</p>
        <p className="mt-0.5 text-sm opacity-80">{message ?? 'Please try again.'}</p>
      </div>
    </div>
  );
}
