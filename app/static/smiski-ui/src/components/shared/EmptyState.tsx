/**
 * EmptyState — consistent "nothing here" view for list/data views.
 */
import type { ReactNode } from 'react';
import { Icon } from '../ui';

export interface EmptyStateProps {
  header: string;
  description?: string;
  primaryAction?: ReactNode;
  secondaryAction?: ReactNode;
}

export function EmptyState({
  header,
  description,
  primaryAction,
  secondaryAction,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed bg-[var(--surface-soft)]/70 px-5 py-8 text-center">
      <div className="mb-3 flex size-11 items-center justify-center rounded-2xl bg-brand-100 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
        <Icon name="calendar" size={21} />
      </div>
      <h3 className="text-sm font-bold text-[var(--text)]">{header}</h3>
      {description && (
        <p className="mt-1 max-w-sm text-sm text-[var(--text-muted)]">{description}</p>
      )}
      {(primaryAction || secondaryAction) && (
        <div className="mt-4 flex flex-wrap justify-center gap-2">
          {primaryAction}
          {secondaryAction}
        </div>
      )}
    </div>
  );
}
