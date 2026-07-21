/**
 * ActionButtonGroup — consistent horizontal row of buttons with the design
 * system's standard spacing. Reused by the Meeting Room control bar and the
 * Recording controls (both need a "row of buttons" layout, no hand-rolled
 * flex/gap repeated in each).
 */
import type { ReactNode } from 'react';
import { cn } from '../ui';

export interface ActionButtonGroupProps {
  children: ReactNode;
  justify?: 'start' | 'center' | 'end' | 'space-between';
}

const JUSTIFY_MAP: Record<NonNullable<ActionButtonGroupProps['justify']>, string> = {
  start: 'justify-start',
  center: 'justify-center',
  end: 'justify-end',
  'space-between': 'justify-between',
};

export function ActionButtonGroup({ children, justify = 'start' }: ActionButtonGroupProps) {
  return (
    <div className={cn('flex flex-wrap items-center gap-2', JUSTIFY_MAP[justify])}>{children}</div>
  );
}
