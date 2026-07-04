'use client';

import * as TabsPrimitive from '@radix-ui/react-tabs';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils.ts';

function Tabs({
    className,
    ref,
    ...props
}: ComponentProps<typeof TabsPrimitive.Root>) {
    return (
        <TabsPrimitive.Root className={cn(className)} ref={ref} {...props} />
    );
}

function TabsList({
    className,
    ref,
    ...props
}: ComponentProps<typeof TabsPrimitive.List>) {
    return (
        <TabsPrimitive.List
            className={cn(
                'inline-flex h-9 items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground',
                className,
            )}
            ref={ref}
            {...props}
        />
    );
}

function TabsTrigger({
    className,
    ref,
    ...props
}: ComponentProps<typeof TabsPrimitive.Trigger>) {
    return (
        <TabsPrimitive.Trigger
            className={cn(
                'inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow',
                className,
            )}
            ref={ref}
            {...props}
        />
    );
}

function TabsContent({
    className,
    ref,
    ...props
}: ComponentProps<typeof TabsPrimitive.Content>) {
    return (
        <TabsPrimitive.Content
            className={cn(
                'mt-2 ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
                className,
            )}
            ref={ref}
            {...props}
        />
    );
}

export { Tabs, TabsContent, TabsList, TabsTrigger };
