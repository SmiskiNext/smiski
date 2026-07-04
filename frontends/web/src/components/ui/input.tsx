import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils.ts';

type InputProps = ComponentProps<'input'>;

function Input({ className, type, ref, ...props }: InputProps) {
    return (
        <input
            className={cn(
                'flex h-9 w-full rounded-md border border-border bg-transparent px-3 py-1 text-base shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50 md:text-sm',
                className,
            )}
            ref={ref}
            type={type}
            {...props}
        />
    );
}

export type { InputProps };
export { Input };
