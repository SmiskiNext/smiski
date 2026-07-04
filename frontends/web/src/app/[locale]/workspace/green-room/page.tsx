import { JoinMeetingContainer } from '@/components/join-meeting/index.tsx';

type GreenRoomPageProps = {
    searchParams: Promise<{ code?: string }>;
};

export default async function GreenRoomPage({
    searchParams,
}: GreenRoomPageProps) {
    const { code } = await searchParams;
    return <JoinMeetingContainer initialCode={code} mode='authenticated' />;
}
