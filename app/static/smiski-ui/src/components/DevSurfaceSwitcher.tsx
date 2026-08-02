import { Button, Icon, SelectDropdown } from './ui';

export type DemoSurface = 'issuePanel' | 'projectPage';

const DEMO_ISSUES = [
    { label: 'SMISKI-101 — running meeting', value: 'SMISKI-101' },
    { label: 'SMISKI-102 — scheduled meetings', value: 'SMISKI-102' },
    { label: 'SMISKI-103 — empty state', value: 'SMISKI-103' },
];

export interface DevSurfaceSwitcherProps {
    surface: DemoSurface;
    onSurfaceChange: (surface: DemoSurface) => void;
    issueKey: string;
    onIssueKeyChange: (issueKey: string) => void;
}

export function DevSurfaceSwitcher({
    surface,
    onSurfaceChange,
    issueKey,
    onIssueKeyChange,
}: DevSurfaceSwitcherProps) {
    return (
        <div className='sticky top-0 z-40 flex flex-wrap items-center gap-2 border-b border-dashed bg-[var(--surface)]/95 px-3 py-2 text-xs shadow-sm backdrop-blur'>
            <span className='mr-1 inline-flex items-center gap-1.5 font-bold text-brand-700 dark:text-brand-300'>
                <Icon name='spark' size={15} />
                Dev preview
            </span>
            <Button
                size='sm'
                variant={surface === 'issuePanel' ? 'primary' : 'ghost'}
                onClick={() => onSurfaceChange('issuePanel')}
            >
                Issue panel
            </Button>
            <Button
                size='sm'
                variant={surface === 'projectPage' ? 'primary' : 'ghost'}
                onClick={() => onSurfaceChange('projectPage')}
            >
                Project page
            </Button>
            {surface === 'issuePanel' && (
                <SelectDropdown
                    className='ml-auto w-60'
                    ariaLabel='Demo issue'
                    value={issueKey}
                    options={DEMO_ISSUES}
                    onChange={onIssueKeyChange}
                />
            )}
        </div>
    );
}
