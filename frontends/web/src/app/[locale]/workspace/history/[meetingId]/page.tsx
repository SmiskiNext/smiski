import { MeetingDetailScreen } from '@/components/meeting-history/meeting-detail-screen.tsx';
import { WorkspaceShell } from '@/components/workspace-shell.tsx';

type WorkspaceHistoryDetailPageProps = {
    params: Promise<{
        meetingId: string;
    }>;
};

export default async function WorkspaceHistoryDetailPage({
    params,
}: WorkspaceHistoryDetailPageProps) {
    const { meetingId } = await params;

    return (
        <WorkspaceShell activeTab='history'>
            <MeetingDetailScreen meetingId={meetingId} />
        </WorkspaceShell>
    );
}
