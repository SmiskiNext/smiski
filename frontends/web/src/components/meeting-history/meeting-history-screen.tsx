'use client';

import { WorkspaceShell } from '@/components/workspace-shell.tsx';
import { MeetingHistoryList } from './meeting-history-list.tsx';
import { useMeetingHistory } from './use-meeting-history.ts';

export function MeetingHistoryScreen() {
    const { state, retry, refresh, loadMore } = useMeetingHistory();

    return (
        <WorkspaceShell activeTab='history'>
            <MeetingHistoryList
                loadMore={loadMore}
                refresh={refresh}
                retry={retry}
                state={state}
            />
        </WorkspaceShell>
    );
}
