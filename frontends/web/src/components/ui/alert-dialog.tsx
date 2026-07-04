'use client';

import * as AlertDialogPrimitive from '@radix-ui/react-alert-dialog';
import type { ComponentProps } from 'react';
import { buttonVariants } from '@/components/ui/button.tsx';
import { cn } from '@/lib/utils.ts';

const AlertDialog = AlertDialogPrimitive.Root;
const AlertDialogTrigger = AlertDialogPrimitive.Trigger;
const AlertDialogPortal = AlertDialogPrimitive.Portal;

function AlertDialogOverlay({
    className,
    ref,
    ...props
}: ComponentProps<typeof AlertDialogPrimitive.Overlay>) {
    return (
        <AlertDialogPrimitive.Overlay
            className={cn(
                'fixed inset-0 z-50 bg-black/80 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
                className,
            )}
            ref={ref}
            {...props}
        />
    );
}

function AlertDialogContent({
    className,
    ref,
    ...props
}: ComponentProps<typeof AlertDialogPrimitive.Content>) {
    return (
        <AlertDialogPortal>
            <AlertDialogOverlay />
            <AlertDialogPrimitive.Content
                className={cn(
                    'fixed left-[50%] top-[50%] z-50 grid w-full max-w-lg translate-x-[-50%] translate-y-[-50%] gap-4 border border-border bg-background p-6 shadow-lg duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%] data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%] sm:rounded-lg',
                    className,
                )}
                ref={ref}
                {...props}
            />
        </AlertDialogPortal>
    );
}

function AlertDialogHeader({ className, ...props }: ComponentProps<'div'>) {
    return (
        <div
            className={cn(
                'flex flex-col space-y-2 text-center sm:text-left',
                className,
            )}
            {...props}
        />
    );
}

function AlertDialogFooter({ className, ...props }: ComponentProps<'div'>) {
    return (
        <div
            className={cn(
                'flex flex-col-reverse gap-2 sm:flex-row sm:justify-end sm:space-x-2',
                className,
            )}
            {...props}
        />
    );
}

function AlertDialogTitle({
    className,
    ref,
    ...props
}: ComponentProps<typeof AlertDialogPrimitive.Title>) {
    return (
        <AlertDialogPrimitive.Title
            className={cn(
                'text-lg font-semibold leading-none tracking-tight',
                className,
            )}
            ref={ref}
            {...props}
        />
    );
}

function AlertDialogDescription({
    className,
    ref,
    ...props
}: ComponentProps<typeof AlertDialogPrimitive.Description>) {
    return (
        <AlertDialogPrimitive.Description
            className={cn('text-sm text-muted-foreground', className)}
            ref={ref}
            {...props}
        />
    );
}

function AlertDialogAction({
    className,
    ref,
    ...props
}: ComponentProps<typeof AlertDialogPrimitive.Action>) {
    return (
        <AlertDialogPrimitive.Action
            className={cn(buttonVariants(), className)}
            ref={ref}
            {...props}
        />
    );
}

function AlertDialogCancel({
    className,
    ref,
    ...props
}: ComponentProps<typeof AlertDialogPrimitive.Cancel>) {
    return (
        <AlertDialogPrimitive.Cancel
            className={cn(
                buttonVariants({ variant: 'outline' }),
                'mt-2 sm:mt-0',
                className,
            )}
            ref={ref}
            {...props}
        />
    );
}

export {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogOverlay,
    AlertDialogPortal,
    AlertDialogTitle,
    AlertDialogTrigger,
};
