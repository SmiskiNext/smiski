import { Track } from 'livekit-client';
import { describe, expect, it } from 'vitest';
import {
    describeRevokedSources,
    diffRevokedPublishSources,
    type RevocableSource,
} from './useLiveKitRoom';

const MIC = Track.sourceToProto(Track.Source.Microphone);
const CAMERA = Track.sourceToProto(Track.Source.Camera);
const SCREEN_SHARE = Track.sourceToProto(Track.Source.ScreenShare);
const SCREEN_SHARE_AUDIO = Track.sourceToProto(Track.Source.ScreenShareAudio);
const UNKNOWN = Track.sourceToProto(Track.Source.Unknown);

describe('diffRevokedPublishSources', () => {
    it('returns nothing when the source list is unchanged', () => {
        expect(
            diffRevokedPublishSources(
                [MIC, CAMERA, SCREEN_SHARE, SCREEN_SHARE_AUDIO],
                [MIC, CAMERA, SCREEN_SHARE, SCREEN_SHARE_AUDIO],
            ),
        ).toEqual([]);
    });

    it('reports only the microphone when just the mic is revoked', () => {
        expect(
            diffRevokedPublishSources(
                [MIC, CAMERA, SCREEN_SHARE, SCREEN_SHARE_AUDIO],
                [CAMERA, SCREEN_SHARE, SCREEN_SHARE_AUDIO],
            ),
        ).toEqual(['microphone']);
    });

    it('reports microphone and camera in a fixed order regardless of input order', () => {
        expect(
            diffRevokedPublishSources(
                [SCREEN_SHARE, CAMERA, MIC],
                [SCREEN_SHARE],
            ),
        ).toEqual(['microphone', 'camera']);
    });

    it('reports screenShare once when screen_share is revoked', () => {
        expect(
            diffRevokedPublishSources(
                [MIC, CAMERA, SCREEN_SHARE, SCREEN_SHARE_AUDIO],
                [MIC, CAMERA],
            ),
        ).toEqual(['screenShare']);
    });

    it('does not report screenShare while screen_share itself is still present, even if screen_share_audio alone drops', () => {
        // The backend always adds/removes screen_share + screen_share_audio as
        // a pair (see `ParticipantGrants.buildAllowedSources`), so this exact
        // partial state never occurs in practice — this just documents that
        // the fold-together grouping treats the group as "still granted" as
        // long as either member remains.
        expect(
            diffRevokedPublishSources(
                [SCREEN_SHARE, SCREEN_SHARE_AUDIO],
                [SCREEN_SHARE],
            ),
        ).toEqual([]);
    });

    it('does not report a source that was gained, only ones that were lost', () => {
        expect(diffRevokedPublishSources([MIC], [MIC, CAMERA])).toEqual([]);
    });

    it('ignores unknown/unmapped proto values without throwing', () => {
        expect(diffRevokedPublishSources([UNKNOWN], [])).toEqual([]);
    });
});

describe('describeRevokedSources', () => {
    it('returns an empty string for no revoked sources', () => {
        expect(describeRevokedSources([])).toBe('');
    });

    it('names a single revoked source', () => {
        expect(describeRevokedSources(['microphone'])).toBe(
            'The host turned off your microphone.',
        );
    });

    it('joins two revoked sources with "and"', () => {
        expect(describeRevokedSources(['microphone', 'camera'])).toBe(
            'The host turned off your microphone and camera.',
        );
    });

    it('joins three revoked sources with an Oxford comma', () => {
        const sources: RevocableSource[] = [
            'microphone',
            'camera',
            'screenShare',
        ];
        expect(describeRevokedSources(sources)).toBe(
            'The host turned off your microphone, camera, and screen sharing.',
        );
    });
});
