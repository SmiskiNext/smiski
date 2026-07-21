import { cn } from './cn';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'warning';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'icon';

const variants: Record<ButtonVariant, string> = {
  primary:
    'border-brand-600 bg-brand-600 text-white shadow-sm hover:border-brand-700 hover:bg-brand-700',
  secondary:
    'border-[var(--border)] bg-[var(--surface)] text-[var(--text)] shadow-sm hover:border-[var(--border-strong)] hover:bg-[var(--surface-soft)]',
  ghost:
    'border-transparent bg-transparent text-[var(--text-muted)] hover:bg-[var(--surface-soft)] hover:text-[var(--text)]',
  danger: 'border-red-600 bg-red-600 text-white shadow-sm hover:border-red-700 hover:bg-red-700',
  warning:
    'border-amber-500 bg-amber-500 text-white shadow-sm hover:border-amber-600 hover:bg-amber-600',
};

const sizes: Record<ButtonSize, string> = {
  sm: 'min-h-8 rounded-md px-2.5 py-1.5 text-xs',
  md: 'min-h-10 rounded-md px-3.5 py-2 text-sm',
  lg: 'min-h-11 rounded-md px-4 py-2.5 text-sm',
  icon: 'size-9 rounded-md p-0',
};

export function buttonClassName(
  variant: ButtonVariant = 'secondary',
  size: ButtonSize = 'md',
): string {
  return cn(
    'inline-flex shrink-0 cursor-pointer items-center justify-center gap-2 border font-semibold transition duration-150',
    'disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50',
    variants[variant],
    sizes[size],
  );
}
