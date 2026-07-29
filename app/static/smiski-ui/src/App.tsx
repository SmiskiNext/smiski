/**
 * App — top-level router between the two Forge module surfaces, plus the app-
 * wide providers (React Query, Jira color mode, current-user identity).
 *
 * Both `jira:issuePanel` and `jira:projectPage` render this same bundle, so App
 * reads the real Forge module context via `view.getContext()` and mounts the
 * matching root component — `context.moduleKey` matches the module `key` in
 * manifest.yml. No business logic here — just surface selection + wiring.
 *
 * In standalone `vite dev` there is no Forge bridge to talk to, so DEV mode
 * skips straight to a DevSurfaceSwitcher-driven local state instead.
 */

import { view } from '@forge/bridge';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, theme } from 'antd';
import { type ReactNode, useEffect, useState } from 'react';
import {
    type DemoSurface,
    DevSurfaceSwitcher,
} from './components/DevSurfaceSwitcher';
import { CurrentUserProvider } from './context/CurrentUserContext';
import type { CurrentIssueContextValue } from './domain';
import { IssuePanelRoot } from './features/issue-panel/IssuePanelRoot';
import { ProjectPageRoot } from './features/project-page/ProjectPageRoot';
import { InstantMeetingModalRoot } from './features/shared/InstantMeetingModalRoot';
import { IssuePanelModalRoot } from './features/shared/IssuePanelModalRoot';
import { ScheduleMeetingModalRoot } from './features/shared/ScheduleMeetingModalRoot';
import {
    type AppColorMode,
    ThemeProvider,
    useResolvedColorMode,
} from './theme/ThemeProvider';
import {
    MODULE_KEY_ISSUE_PANEL,
    MODULE_KEY_PROJECT_PAGE,
} from './utils/forgeModuleKeys';
import {
    INSTANT_MEETING_MODAL_KIND,
    type InstantMeetingModalContext,
    isInstantMeetingModalContext,
} from './utils/instantMeetingModalContext';
import {
    type IssuePanelModalContext,
    isIssuePanelModalContext,
} from './utils/issuePanelModalContext';
import {
    SCHEDULE_MEETING_MODAL_KIND,
    type ScheduleMeetingModalContext,
} from './utils/scheduleMeetingModalContext';

type Surface = DemoSurface | 'loading' | 'unknown' | 'modal';

const queryClient = new QueryClient();

const BRAND_COLOR = '#3385f0';

/**
 * Applies Ant Design theming from the app's resolved color mode: the brand
 * token plus the dark/light algorithm so Ant Design components follow the same
 * light/dark mode as the Tailwind surfaces.
 */
function AntThemeProvider({
    colorMode,
    children,
}: {
    colorMode: AppColorMode;
    children: ReactNode;
}) {
    const resolved = useResolvedColorMode(colorMode);
    return (
        <ConfigProvider
            theme={{
                token: { colorPrimary: BRAND_COLOR },
                algorithm:
                    resolved === 'dark'
                        ? theme.darkAlgorithm
                        : theme.defaultAlgorithm,
            }}
        >
            {children}
        </ConfigProvider>
    );
}
type PlatformModalContext =
    | ScheduleMeetingModalContext
    | InstantMeetingModalContext
    | IssuePanelModalContext;

interface IssuePanelExtension {
    issue?: { key: string; id: string };
    project?: { key: string; id?: string };
}

interface ProjectPageExtension {
    project?: { key: string; id?: string };
}

export function App() {
    const [surface, setSurface] = useState<Surface>('loading');
    const [modalPayload, setModalPayload] =
        useState<PlatformModalContext | null>(null);
    const [issue, setIssue] = useState<CurrentIssueContextValue | null>(null);
    const [colorMode, setColorMode] = useState<AppColorMode>('auto');
    const [projectKey, setProjectKey] = useState('SMISKI');
    const [demoIssueKey, setDemoIssueKey] = useState('SMISKI-101');

    useEffect(() => {
        if (import.meta.env.DEV) {
            // No real Forge context outside Jira — DevSurfaceSwitcher drives `surface`
            // and `demoIssueKey` below instead.
            setSurface('issuePanel');
            return;
        }

        view.getContext()
            .then((context) => {
                const forgeTheme = context.theme as
                    | { colorMode?: AppColorMode }
                    | undefined;
                setColorMode(forgeTheme?.colorMode ?? 'auto');

                const modalContext = context.extension?.modal;
                if (
                    (modalContext as { kind?: unknown } | undefined)?.kind
                    === SCHEDULE_MEETING_MODAL_KIND
                ) {
                    setModalPayload(
                        modalContext as ScheduleMeetingModalContext,
                    );
                    setSurface('modal');
                    return;
                }
                if (isInstantMeetingModalContext(modalContext)) {
                    setModalPayload(modalContext);
                    setSurface('modal');
                    return;
                }
                if (isIssuePanelModalContext(modalContext)) {
                    setModalPayload(modalContext);
                    setSurface('modal');
                    return;
                }

                if (context.moduleKey === MODULE_KEY_ISSUE_PANEL) {
                    const extension = context.extension as IssuePanelExtension;
                    if (extension.issue) {
                        setIssue({
                            issueKey: extension.issue.key,
                            issueId: extension.issue.id,
                            projectKey: extension.project?.key ?? '',
                        });
                    }
                    setSurface('issuePanel');
                } else if (context.moduleKey === MODULE_KEY_PROJECT_PAGE) {
                    const extension = context.extension as ProjectPageExtension;
                    setProjectKey(extension.project?.key ?? 'SMISKI');
                    setSurface('projectPage');
                } else {
                    setSurface('unknown');
                }
            })
            .catch(() => setSurface('unknown'));
    }, []);

    const currentIssue: CurrentIssueContextValue = issue ?? {
        issueKey: demoIssueKey,
        issueId: `id-${demoIssueKey}`,
        projectKey: 'SMISKI',
    };

    return (
        <QueryClientProvider client={queryClient}>
            <AntThemeProvider colorMode={colorMode}>
                <ThemeProvider colorMode={colorMode}>
                    <CurrentUserProvider>
                        {import.meta.env.DEV
                            && (surface === 'issuePanel'
                                || surface === 'projectPage') && (
                                <DevSurfaceSwitcher
                                    surface={surface}
                                    onSurfaceChange={setSurface}
                                    issueKey={demoIssueKey}
                                    onIssueKeyChange={setDemoIssueKey}
                                />
                            )}
                        {surface === 'issuePanel' && (
                            <IssuePanelRoot
                                issue={currentIssue}
                                onDevNavigateToProjectPage={() =>
                                    setSurface('projectPage')
                                }
                            />
                        )}
                        {surface === 'projectPage' && (
                            <ProjectPageRoot projectKey={projectKey} />
                        )}
                        {surface === 'modal'
                            && modalPayload?.kind
                                === SCHEDULE_MEETING_MODAL_KIND && (
                                <ScheduleMeetingModalRoot
                                    payload={modalPayload}
                                />
                            )}
                        {surface === 'modal'
                            && modalPayload?.kind
                                === INSTANT_MEETING_MODAL_KIND && (
                                <InstantMeetingModalRoot
                                    payload={modalPayload}
                                />
                            )}
                        {surface === 'modal'
                            && modalPayload
                            && isIssuePanelModalContext(modalPayload) && (
                                <IssuePanelModalRoot payload={modalPayload} />
                            )}
                        {surface === 'loading' && (
                            <div className='p-6 text-sm text-[var(--text-muted)]'>
                                Loading Smiski…
                            </div>
                        )}
                        {surface === 'unknown' && (
                            <div className='m-4 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-300'>
                                Unable to determine the Smiski surface.
                            </div>
                        )}
                    </CurrentUserProvider>
                </ThemeProvider>
            </AntThemeProvider>
        </QueryClientProvider>
    );
}
