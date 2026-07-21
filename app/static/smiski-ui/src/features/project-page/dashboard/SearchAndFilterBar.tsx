import type { MeetingStatus } from '../../../domain';
import { useProjectIssues } from '../../../hooks/useProjectIssues';
import { useProjectMembers } from '../../../hooks/useProjectMembers';
import { Button, Icon, SelectDropdown } from '../../../components/ui';
import { MEETING_STATUS_FILTER_OPTIONS } from '../../../components/shared';

export interface MeetingFilterValue {
  search?: string;
  issueKey?: string;
  createdByAccountId?: string;
  status?: MeetingStatus;
}

export interface SearchAndFilterBarProps {
  projectKey: string;
  value: MeetingFilterValue;
  onChange: (value: MeetingFilterValue) => void;
}

const compactControl = 'field-control h-8 rounded py-1 text-sm shadow-none';

export function SearchAndFilterBar({ projectKey, value, onChange }: SearchAndFilterBarProps) {
  const {
    issues,
    loading: issuesLoading,
    error: issuesError,
  } = useProjectIssues(projectKey);
  const { members } = useProjectMembers(projectKey);
  const issueOptions = [
    {
      value: '',
      label: issuesLoading
        ? 'Loading issues...'
        : issuesError
          ? 'Issues unavailable'
          : 'All issues',
    },
    ...issues.map((issue) => ({
      value: issue.key,
      label: `${issue.key} — ${issue.summary}`,
    })),
  ];
  const creatorOptions = [
    { value: '', label: 'All creators' },
    ...members.map((user) => ({ value: user.accountId, label: user.displayName })),
  ];
  const hasActiveFilters = Boolean(
    value.search || value.issueKey || value.createdByAccountId || value.status,
  );

  return (
    <div
      className="flex flex-wrap items-center gap-2"
      role="search"
      aria-label="Search and filter meetings"
    >
      <label className="relative min-w-52 flex-1 sm:max-w-xs">
        <span className="sr-only">Search meeting title</span>
        <Icon
          name="search"
          size={14}
          className="absolute top-1/2 left-2.5 -translate-y-1/2 text-[var(--text-faint)]"
        />
        <input
          className={`${compactControl} pl-8`}
          placeholder="Search meetings"
          value={value.search ?? ''}
          onChange={(event) => onChange({ ...value, search: event.target.value })}
        />
      </label>
      <SelectDropdown
        className="w-36"
        ariaLabel="Filter by status"
        value={value.status ?? ''}
        options={MEETING_STATUS_FILTER_OPTIONS}
        onChange={(status) =>
          onChange({
            ...value,
            status: (status || undefined) as MeetingStatus | undefined,
          })
        }
      />
      <SelectDropdown
        className="w-64"
        ariaLabel={issuesError ? 'Issue filter unavailable' : 'Filter by issue'}
        value={value.issueKey ?? ''}
        options={issueOptions}
        disabled={issuesLoading || Boolean(issuesError)}
        onChange={(issueKey) => onChange({ ...value, issueKey: issueKey || undefined })}
      />
      <SelectDropdown
        className="w-40"
        ariaLabel="Filter by creator"
        value={value.createdByAccountId ?? ''}
        options={creatorOptions}
        onChange={(createdByAccountId) =>
          onChange({ ...value, createdByAccountId: createdByAccountId || undefined })
        }
      />
      {hasActiveFilters && (
        <Button
          size="sm"
          variant="ghost"
          className="h-8 min-h-0 rounded"
          onClick={() => onChange({})}
        >
          Clear
        </Button>
      )}
    </div>
  );
}
