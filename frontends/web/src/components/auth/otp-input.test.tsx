import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { OtpInput } from './otp-input.tsx';

describe('OtpInput', () => {
    it('renders 6 input boxes by default', () => {
        render(<OtpInput value='' onChange={vi.fn()} />);
        const inputs = screen.getAllByRole('textbox');
        expect(inputs).toHaveLength(6);
    });

    it('auto-focuses next box on digit entry', () => {
        const onChange = vi.fn();
        render(<OtpInput value='' onChange={onChange} />);
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        fireEvent.change(inputs[0], { target: { value: '1' } });

        expect(onChange).toHaveBeenCalledWith('1');
        expect(document.activeElement).toBe(inputs[1]);
    });

    it('moves to previous box on backspace when current is empty', () => {
        const onChange = vi.fn();
        render(<OtpInput value='12' onChange={onChange} />);
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        fireEvent.keyDown(inputs[2], { key: 'Backspace' });

        expect(document.activeElement).toBe(inputs[1]);
    });

    it('handles arrow key navigation', () => {
        render(<OtpInput value='123' onChange={vi.fn()} />);
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        inputs[2].focus();
        fireEvent.keyDown(inputs[2], { key: 'ArrowLeft' });
        expect(document.activeElement).toBe(inputs[1]);

        fireEvent.keyDown(inputs[1], { key: 'ArrowRight' });
        expect(document.activeElement).toBe(inputs[2]);
    });

    it('distributes pasted 6-digit string across boxes', () => {
        const onChange = vi.fn();
        const onComplete = vi.fn();
        render(
            <OtpInput value='' onChange={onChange} onComplete={onComplete} />,
        );
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        fireEvent.paste(inputs[0], {
            clipboardData: { getData: () => '123456' },
        });

        expect(onChange).toHaveBeenCalledWith('123456');
        expect(onComplete).toHaveBeenCalledWith('123456');
    });

    it('ignores non-digit characters in paste', () => {
        const onChange = vi.fn();
        render(<OtpInput value='' onChange={onChange} />);
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        fireEvent.paste(inputs[0], {
            clipboardData: { getData: () => 'abc123def' },
        });

        expect(onChange).toHaveBeenCalledWith('123');
    });

    it('calls onComplete when all 6 digits are entered', () => {
        const onChange = vi.fn();
        const onComplete = vi.fn();
        render(
            <OtpInput
                value='12345'
                onChange={onChange}
                onComplete={onComplete}
            />,
        );
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        fireEvent.change(inputs[5], { target: { value: '6' } });

        expect(onComplete).toHaveBeenCalledWith('123456');
    });

    it('applies error styling when error prop is true', () => {
        render(<OtpInput value='' onChange={vi.fn()} error={true} />);
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        expect(inputs[0]).toHaveClass('border-error');
    });

    it('disables inputs when disabled prop is true', () => {
        render(<OtpInput value='' onChange={vi.fn()} disabled={true} />);
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        expect(inputs[0]).toBeDisabled();
    });

    it('has proper ARIA labels', () => {
        render(<OtpInput value='' onChange={vi.fn()} />);
        const inputs = screen.getAllByRole('textbox') as HTMLInputElement[];

        expect(inputs[0]).toHaveAttribute('aria-label', 'Digit 1 of 6');
        expect(inputs[5]).toHaveAttribute('aria-label', 'Digit 6 of 6');
    });
});
