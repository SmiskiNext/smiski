import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * shadcn-style class-name helper: merges conditional class values (clsx) and
 * resolves conflicting Tailwind utilities so the last conflicting class wins
 * (tailwind-merge). Accepts the same range of inputs as the previous utility,
 * so existing call sites keep compiling.
 */
export function cn(...inputs: ClassValue[]): string {
    return twMerge(clsx(inputs));
}
