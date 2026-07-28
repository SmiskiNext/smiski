import { describe, expect, it } from 'vitest';
import { cn } from './cn';

describe('cn class-name utility', () => {
    it('resolves conflicting Tailwind classes to the last one', () => {
        const result = cn('p-2', 'p-4');
        expect(result).toContain('p-4');
        expect(result).not.toContain('p-2');
    });

    it('ignores falsy inputs and keeps only truthy class names', () => {
        expect(cn('a', false, null, undefined, 'b')).toBe('a b');
    });

    it('merges conditional (object/array) inputs', () => {
        expect(cn('base', { active: true, hidden: false }, ['x', 'y'])).toBe(
            'base active x y',
        );
    });
});
