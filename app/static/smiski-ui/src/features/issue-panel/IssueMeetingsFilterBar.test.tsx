// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@forge/bridge', () => ({ invoke: vi.fn(), invokeRemote: vi.fn() }));
vi.mock('../../components/shared', () => ({
    MEETING_STATUS_FILTER_OPTIONS: [
        { value: '', label: 'All statuses' },
        { value: 'RUNNING', label: 'Running' },
        { value: 'SCHEDULED', label: 'Scheduled' },
        { value: 'COMPLETED', label: 'Completed' },
        { value: 'CANCELED', label: 'Canceled' },
    ],
}));

import { IssueMeetingsFilterBar } from './IssueMeetingsFilterBar';

describe('IssueMeetingsFilterBar', () => {
    afterEach(cleanup);

    it('reserves iframe height while the status menu is open', () => {
        const { container } = render(
            <IssueMeetingsFilterBar value={{}} onChange={vi.fn()} />,
        );

        const filterBar = container.querySelector('search');
        if (!filterBar) {
            throw new Error('Expected the filter search landmark to render');
        }
        const trigger = screen.getByRole('button', {
            name: 'Filter by status',
        });
        expect(filterBar.classList.contains('pb-52')).toBe(false);

        fireEvent.click(trigger);
        expect(filterBar.classList.contains('pb-52')).toBe(true);

        fireEvent.click(trigger);
        expect(filterBar.classList.contains('pb-52')).toBe(false);
    });
});
