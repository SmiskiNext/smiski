import { useEffect, useState } from 'react';
import { consumePendingMeetingRoom } from '../../utils/meetingRoomHandoff';
import { Dashboard } from './dashboard/Dashboard';
import { MeetingRoom } from './meeting-room/MeetingRoom';

export interface ProjectPageRootProps {
    projectKey: string;
}

export function ProjectPageRoot({ projectKey }: ProjectPageRootProps) {
    const [roomMeetingId, setRoomMeetingId] = useState<string | null>(null);

    // Issue Panel hands off "open this meeting's room" via localStorage before
    // navigating here (separate Forge module = separate iframe, so it can't be
    // passed as a prop) — see hooks/useNavigateToMeetingRoom.ts.
    useEffect(() => {
        const pendingMeetingId = consumePendingMeetingRoom(projectKey);
        if (pendingMeetingId) setRoomMeetingId(pendingMeetingId);
    }, [projectKey]);

    return (
        <main className='bg-[var(--app-bg)]'>
            {roomMeetingId ? (
                <MeetingRoom
                    meetingId={roomMeetingId}
                    onLeave={() => setRoomMeetingId(null)}
                />
            ) : (
                <Dashboard
                    projectKey={projectKey}
                    onOpenRoom={setRoomMeetingId}
                />
            )}
        </main>
    );
}
