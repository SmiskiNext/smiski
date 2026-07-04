import { JoinMeetingContainer } from '@/components/join-meeting/index.tsx';

type GuestJoinPageProps = {
    params: Promise<{ code: string }>;
    searchParams: Promise<{ token?: string }>;
};

export default async function GuestJoinPage({
    params,
    searchParams,
}: GuestJoinPageProps) {
    const { code } = await params;
    const { token } = await searchParams;
    return (
        <JoinMeetingContainer
            initialCode={code}
            inviteToken={token}
            mode='guest'
        />
    );
}
