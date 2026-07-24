/**
 * IssuePanelRoot — entry point for the `jira:issueContext` module.
 *
 * Composes the per-Issue meeting experience (see IssueMeetingsPanel), scoped
 * to the Issue via an explicit `issue` prop — a single consumer at this depth
 * doesn't need a context indirection. Designed for the narrow (~280-320px)
 * Issue Panel sidebar: compact spacing, vertical stacking. Header styling
 * mirrors DashboardHeader (project-page) so both surfaces read as one product.
 */
import type { CurrentIssueContextValue } from '../../domain';
import { IssueMeetingsPanel } from './IssueMeetingsPanel';

export interface IssuePanelRootProps {
    issue: CurrentIssueContextValue;
    onDevNavigateToProjectPage?: () => void;
}

export function IssuePanelRoot({
    issue,
    onDevNavigateToProjectPage,
}: IssuePanelRootProps) {
    return (
        <main className='mx-auto flex w-full max-w-md flex-col bg-[var(--app-bg)]'>
            <header className='flex items-center justify-between border-b bg-[var(--surface)] px-3 py-3 sm:px-4'>
                <h1 className='text-lg font-semibold tracking-tight text-[var(--text)]'>
                    Meetings
                </h1>
                <span className='rounded-lg bg-[var(--surface-strong)] px-2.5 py-1 text-xs font-bold text-[var(--text-muted)]'>
                    {issue.issueKey}
                </span>
            </header>
            <div className='flex flex-col px-3 py-4 sm:px-4'>
                <IssueMeetingsPanel
                    issue={issue}
                    onDevNavigateToProjectPage={onDevNavigateToProjectPage}
                />
            </div>
        </main>
    );
}
