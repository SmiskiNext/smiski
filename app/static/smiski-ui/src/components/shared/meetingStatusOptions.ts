/**
 * Shared status-filter options — used by the project-page dashboard's
 * SearchAndFilterBar and the Issue Panel's IssueMeetingsFilterBar so the
 * label set can't drift between the two surfaces.
 */
import type { MeetingStatus } from '../../domain';

export interface MeetingStatusOption {
    value: MeetingStatus | '';
    label: string;
}

export const MEETING_STATUS_FILTER_OPTIONS: MeetingStatusOption[] = [
    { value: '', label: 'All statuses' },
    { value: 'RUNNING', label: 'Running' },
    { value: 'SCHEDULED', label: 'Scheduled' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELED', label: 'Canceled' },
];
