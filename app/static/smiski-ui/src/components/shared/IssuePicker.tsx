/**
 * IssuePicker — a searchable combobox for binding a meeting to a real Jira
 * issue in the current project. Types a term, lists matching issues (live from
 * Jira via useProjectIssues), and reports the chosen issue key upward.
 */
import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useProjectIssues } from '../../hooks/useProjectIssues';
import { Icon } from '../ui';
import { cn } from '../ui/cn';
import { useFloatingPosition } from '../ui/useFloatingPosition';

export interface IssuePickerProps {
    projectKey: string;
    /** Selected issue key, or '' when nothing is chosen. */
    value: string;
    onChange: (issueKey: string) => void;
    autoFocus?: boolean;
    invalid?: boolean;
    placeholder?: string;
}

export function IssuePicker({
    projectKey,
    value,
    onChange,
    autoFocus,
    invalid,
    placeholder,
}: IssuePickerProps) {
    const [isOpen, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [debouncedQuery, setDebouncedQuery] = useState('');
    const [selectedSummary, setSelectedSummary] = useState<string | null>(null);
    const rootRef = useRef<HTMLDivElement>(null);
    const listRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const listboxId = useId();
    const position = useFloatingPosition(rootRef, isOpen);

    useEffect(() => {
        if (autoFocus) inputRef.current?.focus();
    }, [autoFocus]);

    // Debounce the search term so each keystroke doesn't hit Jira.
    useEffect(() => {
        const timer = setTimeout(() => setDebouncedQuery(query), 250);
        return () => clearTimeout(timer);
    }, [query]);

    const { issues, loading, error } = useProjectIssues(
        projectKey,
        debouncedQuery,
        isOpen || Boolean(value),
    );

    useEffect(() => {
        if (!isOpen) return;
        const handlePointerDown = (event: MouseEvent) => {
            const target = event.target as Node;
            if (
                rootRef.current?.contains(target)
                || listRef.current?.contains(target)
            )
                return;
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

    const select = (key: string, summary: string) => {
        onChange(key);
        setSelectedSummary(summary);
        setQuery('');
        setOpen(false);
    };

    const clear = () => {
        onChange('');
        setSelectedSummary(null);
        setQuery('');
    };

    // What the input shows: the live query while open, otherwise the selection.
    const inputValue = isOpen ? query : value ? value : '';

    return (
        <div ref={rootRef} className='relative'>
            <div
                className={cn(
                    'field-control flex items-center gap-2 py-0 pr-2',
                    invalid
                        && 'border-red-400 focus-within:border-red-500 focus-within:ring-red-500/20',
                )}
            >
                <Icon
                    name='search'
                    size={14}
                    className='shrink-0 text-[var(--text-faint)]'
                />
                <input
                    ref={inputRef}
                    className='h-9 w-full min-w-0 bg-transparent text-sm text-[var(--text)] placeholder:text-[var(--text-faint)] focus:outline-none'
                    value={inputValue}
                    aria-label='Search project issues'
                    aria-expanded={isOpen}
                    aria-controls={listboxId}
                    role='combobox'
                    placeholder={
                        placeholder ?? `Search issues in ${projectKey}`
                    }
                    onFocus={() => setOpen(true)}
                    onChange={(event) => {
                        setQuery(event.target.value);
                        setOpen(true);
                    }}
                />
                {value && !isOpen && (
                    <button
                        type='button'
                        aria-label='Clear selected issue'
                        className='shrink-0 rounded p-0.5 text-[var(--text-faint)] hover:bg-[var(--surface-soft)] hover:text-[var(--text)]'
                        onClick={clear}
                    >
                        <Icon name='x' size={14} />
                    </button>
                )}
            </div>

            {value && !isOpen && selectedSummary && (
                <p className='mt-1 truncate text-xs text-[var(--text-faint)]'>
                    {selectedSummary}
                </p>
            )}

            {isOpen
                && position
                && createPortal(
                    <div
                        ref={listRef}
                        id={listboxId}
                        role='listbox'
                        aria-label={`Issues in ${projectKey}`}
                        style={{
                            top: position.top,
                            left: position.left,
                            width: position.width,
                        }}
                        className='scrollbar-subtle fixed z-50 max-h-72 overflow-auto rounded-md border bg-[var(--surface)] p-1 shadow-card'
                    >
                        {loading ? (
                            <p className='px-2.5 py-3 text-xs text-[var(--text-faint)]'>
                                Loading issues…
                            </p>
                        ) : error ? (
                            <p className='px-2.5 py-3 text-xs text-red-600 dark:text-red-300'>
                                {error.message}
                            </p>
                        ) : issues.length === 0 ? (
                            <p className='px-2.5 py-3 text-xs text-[var(--text-faint)]'>
                                No matching issues in {projectKey}.
                            </p>
                        ) : (
                            issues.map((issue) => {
                                const isSelected = issue.key === value;
                                return (
                                    <button
                                        key={issue.id}
                                        type='button'
                                        role='option'
                                        aria-selected={isSelected}
                                        className={cn(
                                            'flex w-full items-center gap-2 rounded-sm px-2.5 py-1.5 text-left transition',
                                            isSelected
                                                ? 'bg-brand-50 dark:bg-brand-500/15'
                                                : 'hover:bg-[var(--surface-soft)]',
                                        )}
                                        onClick={() =>
                                            select(issue.key, issue.summary)
                                        }
                                    >
                                        <span className='shrink-0 text-xs font-semibold text-brand-700 dark:text-brand-300'>
                                            {issue.key}
                                        </span>
                                        <span className='truncate text-xs text-[var(--text-muted)]'>
                                            {issue.summary}
                                        </span>
                                        {isSelected && (
                                            <Icon
                                                name='check'
                                                size={14}
                                                className='ml-auto shrink-0 text-brand-600'
                                            />
                                        )}
                                    </button>
                                );
                            })
                        )}
                    </div>,
                    document.body,
                )}
        </div>
    );
}
