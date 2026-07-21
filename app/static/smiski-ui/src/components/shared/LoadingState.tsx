/**
 * LoadingState — consistent loading placeholder for list/data views.
 */

export interface LoadingStateProps {
  label?: string;
}

export function LoadingState({ label = 'Loading…' }: LoadingStateProps) {
  return (
    <div
      role="status"
      aria-label={label}
      className="flex items-center gap-2.5 px-2 py-5 text-sm text-[var(--text-muted)]"
    >
      <span
        className="size-4 animate-spin rounded-full border-2 border-brand-500 border-r-transparent"
        aria-hidden="true"
      />
      <span>{label}</span>
    </div>
  );
}
