'use client';

import * as AvatarPrimitive from '@radix-ui/react-avatar';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils.ts';

function Avatar({
    className,
    ref,
    ...props
}: ComponentProps<typeof AvatarPrimitive.Root>) {
    return (
        <AvatarPrimitive.Root
            className={cn(
                'relative flex h-10 w-10 shrink-0 overflow-hidden rounded-full',
                className,
            )}
            ref={ref}
            {...props}
        />
    );
}

function AvatarImage({
    className,
    ref,
    ...props
}: ComponentProps<typeof AvatarPrimitive.Image>) {
    return (
        <AvatarPrimitive.Image
            className={cn('aspect-square h-full w-full', className)}
            ref={ref}
            {...props}
        />
    );
}

function AvatarFallback({
    className,
    ref,
    ...props
}: ComponentProps<typeof AvatarPrimitive.Fallback>) {
    return (
        <AvatarPrimitive.Fallback
            className={cn(
                'flex h-full w-full items-center justify-center rounded-full bg-muted',
                className,
            )}
            ref={ref}
            {...props}
        />
    );
}

export { Avatar, AvatarFallback, AvatarImage };
