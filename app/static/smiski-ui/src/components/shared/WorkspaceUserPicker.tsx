/**
 * WorkspaceUserPicker — an Ant Design multi-select that invites Jira site
 * (workspace) users to a meeting. Typing issues a debounced, server-side search
 * (`useWorkspaceUsers`); each selection keeps the full invitee identity
 * (`accountId`, `displayName`, `email`) needed to create the meeting.
 *
 * Selected invitees are merged into the option list so they stay displayed even
 * when the current search results don't include them, and the picker keeps its
 * selection through loading, empty-result, and error states so the form can
 * still be submitted after a failed search.
 */
import { Select, Spin } from 'antd';
import { useMemo, useState } from 'react';
import type { WorkspaceUser } from '../../api/workspaceUsers';
import { useWorkspaceUsers } from '../../hooks/useWorkspaceUsers';
import {
    mergeInviteeOptions,
    resolveSelectedInvitees,
} from './inviteeIdentity';

export interface WorkspaceUserPickerProps {
    /** Currently selected invitees, retained with full identity. */
    value: WorkspaceUser[];
    onChange: (invitees: WorkspaceUser[]) => void;
    placeholder?: string;
    disabled?: boolean;
    ariaLabel?: string;
}

function optionLabel(user: WorkspaceUser): string {
    return user.email
        ? `${user.displayName} (${user.email})`
        : user.displayName;
}

export function WorkspaceUserPicker({
    value,
    onChange,
    placeholder,
    disabled,
    ariaLabel,
}: WorkspaceUserPickerProps) {
    const [query, setQuery] = useState('');
    const { users, loading, error } = useWorkspaceUsers(query);

    const selectedIds = useMemo(
        () => value.map((user) => user.accountId),
        [value],
    );

    // Merge search results with the current selection so already-picked
    // invitees remain visible even when they aren't in the latest results.
    const options = useMemo(
        () =>
            mergeInviteeOptions(value, users).map((user) => ({
                value: user.accountId,
                label: optionLabel(user),
            })),
        [users, value],
    );

    const handleChange = (ids: string[]) => {
        onChange(resolveSelectedInvitees(ids, value, users));
    };

    const notFoundContent = loading ? (
        <Spin size='small' />
    ) : error ? (
        <span>Could not search users. Your selection is kept.</span>
    ) : (
        <span>No matching users.</span>
    );

    return (
        <Select<string[]>
            mode='multiple'
            allowClear
            disabled={disabled}
            style={{ width: '100%' }}
            aria-label={ariaLabel ?? 'Invitees'}
            placeholder={placeholder ?? 'Invite workspace users'}
            value={selectedIds}
            filterOption={false}
            onSearch={setQuery}
            searchValue={query}
            loading={loading}
            notFoundContent={notFoundContent}
            options={options}
            onChange={handleChange}
        />
    );
}
