export type MeetingParticipant = {
    id: string;
    name: string;
    initials: string;
    isHost: boolean;
    micOn: boolean;
    isSpeaking: boolean;
    tileBg: string;
    avatarGradient: string;
};

export const MEETING_PARTICIPANTS: MeetingParticipant[] = [
    {
        id: 'sarah',
        name: 'Sarah Jenkins',
        initials: 'SJ',
        isHost: true,
        micOn: true,
        isSpeaking: false,
        tileBg: 'bg-[linear-gradient(160deg,_var(--tile-bg-navy-start)_0%,_var(--tile-bg-navy-mid)_50%,_var(--tile-bg-navy-end)_100%)]',
        avatarGradient:
            'from-[var(--avatar-gradient-navy-start)] to-[var(--avatar-gradient-navy-end)]',
    },
    {
        id: 'david',
        name: 'David Chen',
        initials: 'DC',
        isHost: false,
        micOn: false,
        isSpeaking: false,
        tileBg: 'bg-[linear-gradient(160deg,_var(--tile-bg-charcoal-start)_0%,_var(--tile-bg-charcoal-mid)_50%,_var(--tile-bg-charcoal-end)_100%)]',
        avatarGradient:
            'from-[var(--avatar-gradient-slate-start)] to-[var(--avatar-gradient-slate-end)]',
    },
    {
        id: 'elena',
        name: 'Elena Rodriguez',
        initials: 'ER',
        isHost: false,
        micOn: true,
        isSpeaking: false,
        tileBg: 'bg-[linear-gradient(160deg,_var(--tile-bg-slate-start)_0%,_var(--tile-bg-slate-mid)_50%,_var(--tile-bg-slate-start)_100%)]',
        avatarGradient:
            'from-[var(--avatar-gradient-blue-start)] to-[var(--avatar-gradient-blue-end)]',
    },
    {
        id: 'marcus',
        name: 'Marcus Thorne',
        initials: 'MT',
        isHost: false,
        micOn: true,
        isSpeaking: true,
        tileBg: 'bg-[linear-gradient(160deg,_var(--tile-bg-teal-start)_0%,_var(--tile-bg-teal-mid)_50%,_var(--tile-bg-teal-start)_100%)]',
        avatarGradient:
            'from-[var(--avatar-gradient-green-start)] to-[var(--avatar-gradient-green-end)]',
    },
];
