import { JoinMeetingContainer } from '@/components/join-meeting/index.tsx';

type InviteJoinPageProps = {
    searchParams: Promise<{ token?: string }>;
};

export default async function InviteJoinPage({
    searchParams,
}: InviteJoinPageProps) {
    const { token } = await searchParams;
    return <JoinMeetingContainer inviteToken={token} mode='guest' />;
}
