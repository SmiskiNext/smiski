/**
 * ParticipantFilmstrip — the compact participant strip shown beside a screen
 * share, replacing the equal-sized `ParticipantVideoGrid` while someone is
 * presenting (the Google Meet arrangement: one large stage, everyone else
 * demoted to thumbnails).
 *
 * Scrolls horizontally under a screen share on narrow viewports and vertically
 * alongside it from `lg` up, so the stage keeps as much room as possible.
 */
import {
    type GridParticipant,
    isLiveParticipant,
} from './ParticipantVideoGrid';
import { ParticipantVideoTile } from './ParticipantVideoTile';

export interface ParticipantFilmstripProps {
    participants: GridParticipant[];
    selfAccountId: string;
    isSelfMicOn: boolean;
}

export function ParticipantFilmstrip({
    participants,
    selfAccountId,
    isSelfMicOn,
}: ParticipantFilmstripProps) {
    return (
        <div className='flex shrink-0 gap-3 overflow-x-auto pb-1 lg:w-44 lg:flex-col lg:overflow-x-visible lg:overflow-y-auto lg:pb-0'>
            {participants.map((participant) => {
                const isSelf = participant.accountId === selfAccountId;
                const isLive = isLiveParticipant(participant);
                return (
                    <div
                        key={participant.accountId}
                        className='aspect-video w-36 shrink-0 sm:w-40 lg:w-full'
                    >
                        <ParticipantVideoTile
                            participant={participant}
                            isSelf={isSelf}
                            isMicOn={
                                isLive
                                    ? participant.isMicOn
                                    : isSelf
                                      ? isSelfMicOn
                                      : undefined
                            }
                            isCameraOn={
                                isLive ? participant.isCameraOn : undefined
                            }
                            videoTrack={
                                isLive ? participant.videoTrack : undefined
                            }
                            audioTrack={
                                isLive ? participant.audioTrack : undefined
                            }
                        />
                    </div>
                );
            })}
        </div>
    );
}
