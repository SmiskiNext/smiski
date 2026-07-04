'use client';

import { useTranslations } from 'next-intl';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getMe, getParticipatedMeetingDetail } from '@/generated/sdk.gen.ts';
import type { MeetingManagementMeetingDetailResponse } from '@/generated/types.gen.ts';
import { ApiError, ApiFailError } from '@/lib/api/types.ts';

const SESSION_EXPIRED_CODES = new Set([
    'UNAUTHORIZED',
    'AUTHENTICATION_REQUIRED',
    'INVALID_TOKEN',
    'TOKEN_EXPIRED',
]);

export type MeetingDetailState =
    | { phase: 'LOADING' }
    | { phase: 'ERROR'; message: string }
    | {
          phase: 'SUCCESS';
          detail: MeetingManagementMeetingDetailResponse;
      };

type UseMeetingDetailResult = {
    state: MeetingDetailState;
    retry: () => void;
};

function isSessionExpiredError(error: unknown): boolean {
    return (
        error instanceof ApiFailError && SESSION_EXPIRED_CODES.has(error.code)
    );
}

export function useMeetingDetail(meetingId: string): UseMeetingDetailResult {
    const t = useTranslations('workspace.history');
    const [state, setState] = useState<MeetingDetailState>({
        phase: 'LOADING',
    });
    const userIdRef = useRef<string | null>(null);
    const inFlightRef = useRef(false);

    const resolveUserId = useCallback(async () => {
        if (userIdRef.current) return userIdRef.current;

        const { data } = await getMe({ throwOnError: true });
        const userId = data?.id;
        if (!userId) {
            throw new ApiFailError('UNAUTHORIZED', t('detailErrorDescription'));
        }
        userIdRef.current = userId;
        return userId;
    }, [t]);

    const getErrorMessage = useCallback(
        (error: unknown) => {
            if (isSessionExpiredError(error)) {
                return t('sessionExpired');
            }
            if (error instanceof ApiError || error instanceof ApiFailError) {
                return error.message;
            }
            return t('detailErrorDescription');
        },
        [t],
    );

    const loadDetail = useCallback(async () => {
        if (inFlightRef.current) return;
        const normalizedMeetingId = meetingId.trim();
        if (!normalizedMeetingId) {
            setState({ phase: 'ERROR', message: t('detailErrorDescription') });
            return;
        }

        inFlightRef.current = true;
        setState({ phase: 'LOADING' });

        try {
            const userId = await resolveUserId();
            const { data } = await getParticipatedMeetingDetail({
                path: { userId, meetingId: normalizedMeetingId },
                throwOnError: true,
            });
            setState({ phase: 'SUCCESS', detail: data ?? {} });
        } catch (error) {
            setState({ phase: 'ERROR', message: getErrorMessage(error) });
        } finally {
            inFlightRef.current = false;
        }
    }, [meetingId, resolveUserId, getErrorMessage, t]);

    useEffect(() => {
        void loadDetail();
    }, [loadDetail]);

    const retry = useCallback(() => {
        void loadDetail();
    }, [loadDetail]);

    return { state, retry };
}
