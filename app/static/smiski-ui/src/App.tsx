/**
 * App — top-level router between the two Forge module surfaces, plus the app-
 * wide providers (React Query, Jira color mode, current-user identity).
 *
 * Both `jira:issueContext` and `jira:projectPage` render this same bundle, so App
 * reads the real Forge module context via `view.getContext()` and mounts the
 * matching root component — `context.moduleKey` matches the module `key` in
 * manifest.yml. No business logic here — just surface selection + wiring.
 *
 * In standalone `vite dev` there is no Forge bridge to talk to, so DEV mode
 * skips straight to a DevSurfaceSwitcher-driven local state instead.
 */
import { useEffect, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { view } from '@forge/bridge';
import { ThemeProvider, type AppColorMode } from './theme/ThemeProvider';
import { CurrentUserProvider } from './context/CurrentUserContext';
import { IssuePanelRoot } from './features/issue-panel/IssuePanelRoot';
import { ProjectPageRoot } from './features/project-page/ProjectPageRoot';
import { DevSurfaceSwitcher, type DemoSurface } from './components/DevSurfaceSwitcher';
import type { CurrentIssueContextValue } from './domain';
import { ScheduleMeetingModalRoot } from './features/shared/ScheduleMeetingModalRoot';
import { IssuePanelModalRoot } from './features/shared/IssuePanelModalRoot';
import {
  SCHEDULE_MEETING_MODAL_KIND,
  type ScheduleMeetingModalContext,
} from './utils/scheduleMeetingModalContext';
import {
  isIssuePanelModalContext,
  type IssuePanelModalContext,
} from './utils/issuePanelModalContext';
import { MODULE_KEY_ISSUE_CONTEXT, MODULE_KEY_PROJECT_PAGE } from './utils/forgeModuleKeys';

type Surface = DemoSurface | 'loading' | 'unknown' | 'modal';

const queryClient = new QueryClient();
type PlatformModalContext = ScheduleMeetingModalContext | IssuePanelModalContext;

interface IssuePanelExtension {
  issue?: { key: string; id: string };
  project?: { key: string; id?: string };
}

interface ProjectPageExtension {
  project?: { key: string; id?: string };
}

export function App() {
  const [surface, setSurface] = useState<Surface>('loading');
  const [modalPayload, setModalPayload] = useState<PlatformModalContext | null>(null);
  const [issue, setIssue] = useState<CurrentIssueContextValue | null>(null);
  const [colorMode, setColorMode] = useState<AppColorMode>('auto');
  const [projectKey, setProjectKey] = useState('SMISKI');
  const [demoIssueKey, setDemoIssueKey] = useState('SMISKI-101');

  useEffect(() => {
    if (import.meta.env.DEV) {
      // No real Forge context outside Jira — DevSurfaceSwitcher drives `surface`
      // and `demoIssueKey` below instead.
      setSurface('issueContext');
      return;
    }

    view
      .getContext()
      .then((context) => {
        const forgeTheme = context.theme as { colorMode?: AppColorMode } | undefined;
        setColorMode(forgeTheme?.colorMode ?? 'auto');

        const modalContext = context.extension?.modal;
        if (
          (modalContext as { kind?: unknown } | undefined)?.kind === SCHEDULE_MEETING_MODAL_KIND
        ) {
          setModalPayload(modalContext as ScheduleMeetingModalContext);
          setSurface('modal');
          return;
        }
        if (isIssuePanelModalContext(modalContext)) {
          setModalPayload(modalContext);
          setSurface('modal');
          return;
        }

        if (context.moduleKey === MODULE_KEY_ISSUE_CONTEXT) {
          const extension = context.extension as IssuePanelExtension;
          if (extension.issue) {
            setIssue({
              issueKey: extension.issue.key,
              issueId: extension.issue.id,
              projectKey: extension.project?.key ?? '',
            });
          }
          setSurface('issueContext');
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

  const issueContextValue: CurrentIssueContextValue = issue ?? {
    issueKey: demoIssueKey,
    issueId: `id-${demoIssueKey}`,
    projectKey: 'SMISKI',
  };

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider colorMode={colorMode}>
        <CurrentUserProvider>
          {import.meta.env.DEV && (surface === 'issueContext' || surface === 'projectPage') && (
            <DevSurfaceSwitcher
              surface={surface}
              onSurfaceChange={setSurface}
              issueKey={demoIssueKey}
              onIssueKeyChange={setDemoIssueKey}
            />
          )}
          {surface === 'issueContext' && (
            <IssuePanelRoot
              issue={issueContextValue}
              onDevNavigateToProjectPage={() => setSurface('projectPage')}
            />
          )}
          {surface === 'projectPage' && <ProjectPageRoot projectKey={projectKey} />}
          {surface === 'modal' && modalPayload?.kind === SCHEDULE_MEETING_MODAL_KIND && (
            <ScheduleMeetingModalRoot payload={modalPayload} />
          )}
          {surface === 'modal' && modalPayload && isIssuePanelModalContext(modalPayload) && (
            <IssuePanelModalRoot payload={modalPayload} />
          )}
          {surface === 'loading' && (
            <div className="p-6 text-sm text-[var(--text-muted)]">Loading Smiski…</div>
          )}
          {surface === 'unknown' && (
            <div className="m-4 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-300">
              Unable to determine the Smiski surface.
            </div>
          )}
        </CurrentUserProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
