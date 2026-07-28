/**
 * IssuePicker — a searchable combobox for binding a meeting to a real Jira
 * issue in the current project. Types a term, lists matching issues (live from
 * Jira via useProjectIssues), and reports the chosen issue key upward.
 *
 * Built on Ant Design `Select` with server-side search: the typed term drives
 * `useProjectIssues`, so filtering happens in Jira, not on the client. The prop
 * and selection contract is unchanged, so dependent forms (ScheduleMeetingModal)
 * keep working.
 */
import { Select } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useProjectIssues } from '../../hooks/useProjectIssues';

export interface IssuePickerProps {
    projectKey: string;
    /** Selected issue key, or '' when nothing is chosen. */
    value: string;
    onChange: (issueKey: string) => void;
    autoFocus?: boolean;
    invalid?: boolean;
    placeholder?: string;
}

interface IssueOption {
    value: string;
    label: string;
    summary: string;
}

export function IssuePicker({
    projectKey,
    value,
    onChange,
    autoFocus,
    invalid,
    placeholder,
}: IssuePickerProps) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [debouncedQuery, setDebouncedQuery] = useState('');
    // Remember the label of the current selection so it stays displayed even
    // when a later search no longer includes that issue in its results.
    const [selectedOption, setSelectedOption] = useState<IssueOption | null>(
        null,
    );

    // Debounce the search term so each keystroke doesn't hit Jira.
    useEffect(() => {
        const timer = setTimeout(() => setDebouncedQuery(query), 250);
        return () => clearTimeout(timer);
    }, [query]);

    const { issues, loading, error } = useProjectIssues(
        projectKey,
        debouncedQuery,
        open || Boolean(value),
    );

    const options = useMemo<IssueOption[]>(() => {
        const fromResults = issues.map((issue) => ({
            value: issue.key,
            label: `${issue.key} — ${issue.summary}`,
            summary: issue.summary,
        }));
        if (
            selectedOption
            && !fromResults.some((option) => option.value === value)
        ) {
            return [selectedOption, ...fromResults];
        }
        return fromResults;
    }, [issues, selectedOption, value]);

    return (
        <Select<string, IssueOption>
            showSearch
            allowClear
            autoFocus={autoFocus}
            open={open}
            onDropdownVisibleChange={setOpen}
            status={invalid ? 'error' : undefined}
            style={{ width: '100%' }}
            placeholder={placeholder ?? `Search issues in ${projectKey}`}
            value={value || undefined}
            filterOption={false}
            onSearch={setQuery}
            searchValue={query}
            notFoundContent={
                loading
                    ? 'Loading issues…'
                    : error
                      ? error.message
                      : `No matching issues in ${projectKey}.`
            }
            options={options}
            onChange={(next, option) => {
                onChange(next ?? '');
                setSelectedOption(
                    next ? ((option as IssueOption) ?? null) : null,
                );
                setQuery('');
            }}
        />
    );
}
