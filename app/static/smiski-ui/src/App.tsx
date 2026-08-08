/**
 * App — top-level router between the two Forge module surfaces, plus the app-
 * wide providers (React Query, Jira color mode, current-user identity).
 *
 * Both `jira:issuePanel` and `jira:projectPage` render this same bundle, so App
 * reads the real Forge module context via `view.getContext()` and mounts the
 * matching root component — `context.moduleKey` matches the module `key` in
 * manifest.yml. No business logic here — just surface selection + wiring.
 *
 * `surface` stays `'loading'` until the gateway context identifiers are
 * published, because every surface root issues backend requests as it mounts and
 * a request without them resolves to an empty permission set. A module whose
 * Forge context lacks the identifiers its surface needs resolves to `'unknown'`
 * rather than a placeholder issue or project key.
 *
 * The query cache is persisted rather than held only in memory. Each Forge
 * module and each platform modal renders in its own iframe, so an in-memory
 * cache starts empty every time one opens and `staleTime` never gets the chance
 * to prevent a refetch. `hooks/queryPersistence.ts` explains what is persisted
 * and why the rest deliberately is not.
 */

import { view } from '@forge/bridge';
import { QueryClient } from '@tanstack/react-query';
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';
import { ConfigProvider, theme } from 'antd';
import { type ReactNode, useEffect, useState } from 'react';
import { publishProjectContext, setBackendContext } from './api/backendContext';
import { CurrentUserProvider } from './context/CurrentUserContext';
import type { CurrentIssueContextValue } from './domain';
import { IssuePanelRoot } from './features/issue-panel/IssuePanelRoot';
import { ProjectPageRoot } from './features/project-page/ProjectPageRoot';
import { InstantMeetingModalRoot } from './features/shared/InstantMeetingModalRoot';
import { IssuePanelModalRoot } from './features/shared/IssuePanelModalRoot';
import { ScheduleMeetingModalRoot } from './features/shared/ScheduleMeetingModalRoot';
import { persistOptions } from './hooks/queryPersistence';
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

type Surface = 'issuePanel' | 'projectPage' | 'loading' | 'unknown' | 'modal';

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
    const [projectKey, setProjectKey] = useState<string | null>(null);

    useEffect(() => {
        view.getContext()
            .then(async (context) => {
                const forgeTheme = context.theme as
                    | { colorMode?: AppColorMode }
                    | undefined;
                setColorMode(forgeTheme?.colorMode ?? 'auto');

                const modalContext = context.extension?.modal;
                if (
                    (modalContext as { kind?: unknown } | undefined)?.kind
                    === SCHEDULE_MEETING_MODAL_KIND
                ) {
                    const payload = modalContext as ScheduleMeetingModalContext;
                    setBackendContext(payload);
                    setModalPayload(payload);
                    setSurface('modal');
                    return;
                }
                if (isInstantMeetingModalContext(modalContext)) {
                    setBackendContext(modalContext);
                    setModalPayload(modalContext);
                    setSurface('modal');
                    return;
                }
                if (isIssuePanelModalContext(modalContext)) {
                    setBackendContext(modalContext);
                    setModalPayload(modalContext);
                    setSurface('modal');
                    return;
                }

                if (context.moduleKey === MODULE_KEY_ISSUE_PANEL) {
                    const extension = context.extension as IssuePanelExtension;
                    if (!extension.issue) {
                        setSurface('unknown');
                        return;
                    }
                    setIssue({
                        issueKey: extension.issue.key,
                        issueId: extension.issue.id,
                        projectKey: extension.project?.key ?? '',
                    });
                    setBackendContext({
                        issueId: extension.issue.id,
                        projectId: extension.project?.id,
                    });
                    setSurface('issuePanel');
                } else if (context.moduleKey === MODULE_KEY_PROJECT_PAGE) {
                    const extension = context.extension as ProjectPageExtension;
                    const resolvedProjectKey = extension.project?.key;
                    if (!resolvedProjectKey) {
                        setSurface('unknown');
                        return;
                    }
                    setProjectKey(resolvedProjectKey);
                    await publishProjectContext(
                        extension.project?.id,
                        resolvedProjectKey,
                    );
                    setSurface('projectPage');
                } else {
                    setSurface('unknown');
                }
            })
            .catch(() => setSurface('unknown'));
    }, []);

    return (
        <PersistQueryClientProvider
            client={queryClient}
            persistOptions={persistOptions}
        >
            <AntThemeProvider colorMode={colorMode}>
                <ThemeProvider colorMode={colorMode}>
                    <CurrentUserProvider>
                        {surface === 'issuePanel' && issue && (
                            <IssuePanelRoot issue={issue} />
                        )}
                        {surface === 'projectPage' && projectKey && (
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
        </PersistQueryClientProvider>
    );
}
