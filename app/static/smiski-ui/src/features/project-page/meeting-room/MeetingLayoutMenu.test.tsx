// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MeetingLayoutMenu } from './MeetingLayoutMenu';

afterEach(cleanup);

function trigger() {
    return screen.getByRole('button', { name: 'Layout' });
}

describe('MeetingLayoutMenu', () => {
    it('renders a closed trigger', () => {
        render(<MeetingLayoutMenu mode='auto' onModeChange={vi.fn()} />);

        expect(trigger().getAttribute('aria-haspopup')).toBe('listbox');
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
        expect(screen.queryByRole('listbox')).toBeNull();
    });

    it('opens the layout options on click', () => {
        render(<MeetingLayoutMenu mode='auto' onModeChange={vi.fn()} />);

        fireEvent.click(trigger());

        expect(trigger().getAttribute('aria-expanded')).toBe('true');
        expect(
            screen.getAllByRole('option').map((option) => option.textContent),
        ).toHaveLength(3);
        expect(screen.getByText('Auto')).toBeDefined();
        expect(screen.getByText('Tiled')).toBeDefined();
        expect(screen.getByText('Spotlight')).toBeDefined();
    });

    it('marks the active mode as selected', () => {
        render(<MeetingLayoutMenu mode='spotlight' onModeChange={vi.fn()} />);

        fireEvent.click(trigger());

        expect(
            screen
                .getByRole('option', { selected: true })
                .textContent?.startsWith('Spotlight'),
        ).toBe(true);
    });

    it('reports the chosen mode and closes', () => {
        const onModeChange = vi.fn();
        render(<MeetingLayoutMenu mode='auto' onModeChange={onModeChange} />);

        fireEvent.click(trigger());
        fireEvent.click(screen.getByText('Tiled'));

        expect(onModeChange).toHaveBeenCalledWith('tiled');
        expect(screen.queryByRole('listbox')).toBeNull();
    });

    it('closes on Escape without changing the mode', () => {
        const onModeChange = vi.fn();
        render(<MeetingLayoutMenu mode='auto' onModeChange={onModeChange} />);

        fireEvent.click(trigger());
        fireEvent.keyDown(document, { key: 'Escape' });

        expect(screen.queryByRole('listbox')).toBeNull();
        expect(onModeChange).not.toHaveBeenCalled();
    });

    it('closes on an outside click without changing the mode', () => {
        const onModeChange = vi.fn();
        render(<MeetingLayoutMenu mode='auto' onModeChange={onModeChange} />);

        fireEvent.click(trigger());
        fireEvent.mouseDown(document.body);

        expect(screen.queryByRole('listbox')).toBeNull();
        expect(onModeChange).not.toHaveBeenCalled();
    });
});
