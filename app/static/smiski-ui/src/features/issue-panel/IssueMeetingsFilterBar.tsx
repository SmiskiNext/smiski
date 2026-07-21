/**
 * IssueMeetingsFilterBar — search-by-title + status filter for the Issue
 * Panel's unified meeting list. Narrower than the project-page dashboard's
 * SearchAndFilterBar (no issue/creator filters — already scoped to one
 * issue), but shares the same status option set for consistency.
 */
import { MEETING_STATUS_FILTER_OPTIONS } from '../../components/shared';
import { Icon, SelectDropdown } from '../../components/ui';
import type { IssueMeetingsFilterValue } from './issueMeetingsFilter';

export interface IssueMeetingsFilterBarProps {
  value: IssueMeetingsFilterValue;
  onChange: (value: IssueMeetingsFilterValue) => void;
}

export function IssueMeetingsFilterBar({ value, onChange }: IssueMeetingsFilterBarProps) {
  return (
    <div className="flex flex-col gap-2" role="search" aria-label="Search and filter meetings">
      <label className="relative block">
        <span className="sr-only">Search meetings by title</span>
        <Icon
          name="search"
          size={14}
          className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-[var(--text-faint)]"
        />
        <input
          className="field-control pl-9"
          placeholder="Search meetings…"
          value={value.search ?? ''}
          onChange={(event) => onChange({ ...value, search: event.target.value || undefined })}
        />
      </label>
      <SelectDropdown
        className="w-full"
        ariaLabel="Filter by status"
        value={value.status ?? ''}
        options={MEETING_STATUS_FILTER_OPTIONS}
        onChange={(status) =>
          onChange({ ...value, status: (status || undefined) as typeof value.status })
        }
      />
    </div>
  );
}
