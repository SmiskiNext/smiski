// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MultiSelectDropdown } from './MultiSelectDropdown';

const OPTIONS = [
    { value: 'RUNNING', label: 'Running' },
    { value: 'SCHEDULED', label: 'Scheduled' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELED', label: 'Canceled' },
];

describe('MultiSelectDropdown', () => {
    afterEach(cleanup);

    it('keeps multiple selections compact in the trigger', () => {
        render(
            <MultiSelectDropdown
                ariaLabel='Filter by status'
                values={OPTIONS.map((option) => option.value)}
                options={OPTIONS}
                onChange={vi.fn()}
                placeholder='All statuses'
            />,
        );

        const trigger = screen.getByRole('button', {
            name: 'Filter by status',
        });
        expect(trigger.textContent).toBe('Running+3');
        expect(trigger.getAttribute('title')).toBe(
            'Running, Scheduled, Completed, Canceled',
        );
    });

    it('shows the placeholder when no values are selected', () => {
        render(
            <MultiSelectDropdown
                ariaLabel='Filter by status'
                values={[]}
                options={OPTIONS}
                onChange={vi.fn()}
                placeholder='All statuses'
            />,
        );

        expect(screen.getByText('All statuses')).toBeDefined();
    });
});
