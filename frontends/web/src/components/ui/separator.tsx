'use client';

import * as SeparatorPrimitive from '@radix-ui/react-separator';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils.ts';

function Separator({
    className,
    orientation = 'horizontal',
    decorative = true,
    ref,
    ...props
}: ComponentProps<typeof SeparatorPrimitive.Root>) {
    return (
        <SeparatorPrimitive.Root
            className={cn(
                'shrink-0 bg-border',
                orientation === 'horizontal'
                    ? 'h-[1px] w-full'
                    : 'h-full w-[1px]',
                className,
            )}
            decorative={decorative}
            orientation={orientation}
            ref={ref}
            {...props}
        />
    );
}

export { Separator };
