/**
 * SectionHeading — small uppercase-ish subsection label, reused across the
 * Issue Panel sections and the Project Page dashboard.
 */
import type { ReactNode } from 'react';

export interface SectionHeadingProps {
  children: ReactNode;
}

export function SectionHeading({ children }: SectionHeadingProps) {
  return (
    <h3 className="mb-2.5 mt-6 text-[11px] font-bold tracking-[0.12em] text-[var(--text-faint)] uppercase">
      {children}
    </h3>
  );
}
