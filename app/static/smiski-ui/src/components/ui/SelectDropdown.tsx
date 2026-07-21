import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { cn } from './cn';
import { Icon } from './Icon';
import { useFloatingPosition } from './useFloatingPosition';

export interface SelectDropdownOption {
  value: string;
  label: string;
}

export interface SelectDropdownProps {
  ariaLabel: string;
  value: string;
  options: SelectDropdownOption[];
  onChange: (value: string) => void;
  className?: string;
  disabled?: boolean;
}

export function SelectDropdown({
  ariaLabel,
  value,
  options,
  onChange,
  className,
  disabled = false,
}: SelectDropdownProps) {
  const [isOpen, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const listboxId = useId();
  const selected = options.find((option) => option.value === value) ?? options[0];
  const position = useFloatingPosition(rootRef, isOpen);

  useEffect(() => {
    if (disabled) setOpen(false);
  }, [disabled]);

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

  return (
    <div ref={rootRef} className={cn('relative shrink-0', className)}>
      <button
        type="button"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={listboxId}
        disabled={disabled}
        className={cn(
          'flex h-8 w-full items-center justify-between gap-2 rounded border bg-[var(--surface)] px-2.5 text-sm font-medium text-[var(--text)] shadow-none transition hover:bg-[var(--surface-soft)] focus:border-brand-500 focus:ring-2 focus:ring-brand-500/25 focus:outline-none',
          isOpen && 'border-brand-500 ring-2 ring-brand-500/25',
          disabled && 'cursor-not-allowed opacity-60 hover:bg-[var(--surface)]',
        )}
        onClick={() => setOpen((open) => !open)}
      >
        <span className="truncate">{selected.label}</span>
        <Icon
          name="chevronDown"
          size={14}
          className={cn(
            'shrink-0 text-[var(--text-faint)] transition-transform',
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
            aria-label={ariaLabel}
            style={{ top: position.top, left: position.left, minWidth: position.width }}
            className="scrollbar-subtle fixed z-50 mt-0 max-h-72 overflow-auto rounded border bg-[var(--surface)] p-1 shadow-card"
          >
            {options.map((option) => {
              const isSelected = option.value === value;
              return (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className={cn(
                    'flex w-full items-center justify-between gap-3 rounded-sm px-2.5 py-1.5 text-left text-sm whitespace-nowrap transition',
                    isSelected
                      ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-300'
                      : 'text-[var(--text)] hover:bg-[var(--surface-soft)]',
                  )}
                  onClick={() => {
                    onChange(option.value);
                    setOpen(false);
                  }}
                >
                  {option.label}
                  {isSelected && <Icon name="check" size={14} />}
                </button>
              );
            })}
          </div>,
          document.body,
        )}
    </div>
  );
}
