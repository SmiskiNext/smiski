import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PasswordStrengthMeter } from './password-strength-meter.tsx';

const labels = {
    weak: 'Weak',
    fair: 'Fair',
    good: 'Good',
    strong: 'Strong',
};

describe('PasswordStrengthMeter', () => {
    it('returns null for empty password', () => {
        const { container } = render(
            <PasswordStrengthMeter password='' labels={labels} />,
        );
        expect(container.firstChild).toBeNull();
    });

    it('shows weak strength for short password', () => {
        render(<PasswordStrengthMeter password='abc' labels={labels} />);
        expect(screen.getByText('Weak')).toBeInTheDocument();
        const progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveAttribute('aria-valuenow', '25');
    });

    it('shows fair strength for password with length and one char class', () => {
        render(<PasswordStrengthMeter password='abcdefgh' labels={labels} />);
        expect(screen.getByText('Weak')).toBeInTheDocument();
        const progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveAttribute('aria-valuenow', '25');
    });

    it('shows good strength for password with length and multiple char classes', () => {
        render(<PasswordStrengthMeter password='Abcdefgh12' labels={labels} />);
        expect(screen.getByText('Good')).toBeInTheDocument();
        const progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveAttribute('aria-valuenow', '75');
    });

    it('shows strong strength for long password with all char classes', () => {
        render(
            <PasswordStrengthMeter password='Abcdefgh123!@#' labels={labels} />,
        );
        expect(screen.getByText('Strong')).toBeInTheDocument();
        const progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveAttribute('aria-valuenow', '100');
    });

    it('has ARIA live region for screen reader announcements', () => {
        render(<PasswordStrengthMeter password='test123' labels={labels} />);
        const liveRegion = screen.getByText(/Weak|Fair|Good|Strong/);
        expect(liveRegion).toHaveAttribute('aria-live', 'polite');
        expect(liveRegion).toHaveAttribute('aria-atomic', 'true');
    });

    it('applies correct color classes', () => {
        const { rerender } = render(
            <PasswordStrengthMeter password='abc' labels={labels} />,
        );
        let progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveClass('bg-error');

        rerender(
            <PasswordStrengthMeter password='abcdefgh1' labels={labels} />,
        );
        progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveClass('bg-warning');

        rerender(
            <PasswordStrengthMeter password='Abcdefgh12' labels={labels} />,
        );
        progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveClass('bg-info');

        rerender(
            <PasswordStrengthMeter password='Abcdefgh123!@#' labels={labels} />,
        );
        progressBar = screen.getByRole('progressbar');
        expect(progressBar).toHaveClass('bg-success');
    });
});
