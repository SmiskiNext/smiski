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

const NO_EXCLUDED_ACCOUNTS: readonly string[] = [];

export interface WorkspaceUserPickerProps {
    /** Currently selected invitees, retained with full identity. */
    value: WorkspaceUser[];
    onChange: (invitees: WorkspaceUser[]) => void;
    placeholder?: string;
    disabled?: boolean;
    ariaLabel?: string;
    /** Account ids that should not be offered as new selections. */
    excludedAccountIds?: readonly string[];
    /** Disable users whose Jira profile does not expose an email address. */
    requireEmail?: boolean;
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
    excludedAccountIds = NO_EXCLUDED_ACCOUNTS,
    requireEmail = false,
}: WorkspaceUserPickerProps) {
    const [query, setQuery] = useState('');
    const { users, loading, error } = useWorkspaceUsers(query);

    const selectedIds = useMemo(
        () => value.map((user) => user.accountId),
        [value],
    );
    const selectedIdSet = useMemo(() => new Set(selectedIds), [selectedIds]);
    const excludedIdSet = useMemo(
        () => new Set(excludedAccountIds),
        [excludedAccountIds],
    );

    // Merge search results with the current selection so already-picked
    // invitees remain visible even when they aren't in the latest results.
    const options = useMemo(
        () =>
            mergeInviteeOptions(value, users)
                .filter(
                    (user) =>
                        selectedIdSet.has(user.accountId)
                        || !excludedIdSet.has(user.accountId),
                )
                .map((user) => ({
                    value: user.accountId,
                    label:
                        requireEmail && !user.email
                            ? `${user.displayName} (email unavailable)`
                            : optionLabel(user),
                    disabled: requireEmail && !user.email,
                })),
        [excludedIdSet, requireEmail, selectedIdSet, users, value],
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
