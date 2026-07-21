import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { cn } from './cn';
import { Icon } from './Icon';
import { useFloatingPosition } from './useFloatingPosition';

export interface MultiSelectOption {
  value: string;
  label: string;
}

export interface MultiSelectDropdownProps {
  ariaLabel: string;
  values: string[];
  options: MultiSelectOption[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
}

export function MultiSelectDropdown({
  ariaLabel,
  values,
  options,
  onChange,
  placeholder = 'Select…',
  className,
  disabled,
}: MultiSelectDropdownProps) {
  const [isOpen, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const listboxId = useId();
  const selectedLabels = options
    .filter((option) => values.includes(option.value))
    .map((option) => option.label);
  const position = useFloatingPosition(rootRef, isOpen);

  useEffect(() => {
    if (!isOpen) return;
    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (rootRef.current?.contains(target) || listRef.current?.contains(target)) return;
      setOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const toggleValue = (value: string) => {
    onChange(values.includes(value) ? values.filter((v) => v !== value) : [...values, value]);
  };

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        type="button"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={listboxId}
        disabled={disabled}
        className={cn(
          'flex min-h-8 w-full items-center justify-between gap-2 rounded border bg-[var(--surface)] px-2.5 py-1 text-left text-sm transition hover:bg-[var(--surface-soft)] focus:border-brand-500 focus:ring-2 focus:ring-brand-500/25 focus:outline-none',
          isOpen && 'border-brand-500 ring-2 ring-brand-500/25',
          disabled && 'cursor-not-allowed opacity-60 hover:bg-[var(--surface)]',
        )}
        onClick={() => !disabled && setOpen((open) => !open)}
      >
        {selectedLabels.length ? (
          <span className="flex flex-wrap gap-1 py-0.5">
            {selectedLabels.map((label) => (
              <span
                key={label}
                className="rounded bg-brand-50 px-1.5 py-0.5 text-xs font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-300"
              >
                {label}
              </span>
            ))}
          </span>
        ) : (
          <span className="text-[var(--text-faint)]">{placeholder}</span>
        )}
        <Icon
          name="chevronDown"
          size={14}
          className={cn(
            'ml-auto shrink-0 text-[var(--text-faint)] transition-transform',
            isOpen && 'rotate-180',
          )}
        />
      </button>

      {isOpen &&
        position &&
        createPortal(
          <div
            ref={listRef}
            id={listboxId}
            role="listbox"
            aria-multiselectable="true"
            aria-label={ariaLabel}
            style={{ top: position.top, left: position.left, width: position.width }}
            className="scrollbar-subtle fixed z-50 max-h-56 overflow-auto rounded border bg-[var(--surface)] p-1 shadow-card"
          >
            {options.map((option) => {
              const isSelected = values.includes(option.value);
              return (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className={cn(
                    'flex w-full items-center gap-2.5 rounded-sm px-2.5 py-1.5 text-left text-sm transition',
                    isSelected
                      ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-300'
                      : 'text-[var(--text)] hover:bg-[var(--surface-soft)]',
                  )}
                  onClick={() => toggleValue(option.value)}
                >
                  <span
                    className={cn(
                      'flex size-4 shrink-0 items-center justify-center rounded-sm border transition',
                      isSelected
                        ? 'border-brand-500 bg-brand-500 text-white'
                        : 'border-[var(--border-strong)] text-transparent',
                    )}
                    aria-hidden="true"
                  >
                    <Icon name="check" size={11} strokeWidth={3} />
                  </span>
                  <span className="truncate">{option.label}</span>
                </button>
              );
            })}
          </div>,
          document.body,
        )}
    </div>
  );
}
