'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { usePreviewTracks } from '@livekit/components-react';
import { type LocalVideoTrack, Track } from 'livekit-client';
import { Mic, MicOff, Video, VideoOff } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/button.tsx';
import type { JoinMode, JoinState } from './use-join-meeting.ts';

type InitialStepValues = {
    code: string;
    displayName: string;
};

type PasswordStepValues = {
    displayName: string;
    password: string;
};

export function buildInitialStepSchema(requiredMessage: string) {
    return z.object({
        code: z.string().min(1, requiredMessage),
        displayName: z.string().min(1, requiredMessage),
    });
}

export function buildPasswordStepSchema(requiredMessage: string) {
    return z.object({
        displayName: z.string().min(1, requiredMessage),
        password: z.string().min(1, requiredMessage),
    });
}

type JoinFormProps = {
    mode: JoinMode;
    initialCode?: string;
    initialDisplayName?: string;
    state: JoinState;
    isValidatingToken?: boolean;
    tokenError?: string | null;
    micEnabled: boolean;
    videoEnabled: boolean;
    onMicChange: (next: boolean) => void;
    onVideoChange: (next: boolean) => void;
    onCodeChange?: () => void;
    onSubmit: (params: {
        code: string;
        displayName: string;
        password?: string;
    }) => void;
    onSubmitPassword: (params: {
        displayName: string;
        password: string;
    }) => void;
};

type PreviewError = 'camera' | 'mic' | null;

export function JoinMeetingForm({
    mode,
    initialCode = '',
    initialDisplayName = '',
    state,
    isValidatingToken = false,
    tokenError = null,
    micEnabled,
    videoEnabled,
    onMicChange,
    onVideoChange,
    onCodeChange,
    onSubmit,
    onSubmitPassword,
}: JoinFormProps) {
    const t = useTranslations('joinMeeting');
    const [previewError, setPreviewError] = useState<PreviewError>(null);
    const videoRef = useRef<HTMLVideoElement | null>(null);

    const previewTracks = usePreviewTracks(
        {
            audio: micEnabled,
            video: videoEnabled,
        },
        (err) => {
            const message = err.message?.toLowerCase() ?? '';
            const name = (err as { name?: string }).name?.toLowerCase() ?? '';
            const isPermissionError =
                name.includes('notallowed')
                || name.includes('permission')
                || message.includes('permission')
                || message.includes('denied');
            if (!isPermissionError) {
                return;
            }
            const blockedAudio =
                message.includes('audio') || name.includes('audio');
            const blockedVideo =
                message.includes('video') || name.includes('video');
            if (videoEnabled && (blockedVideo || !blockedAudio)) {
                onVideoChange(false);
                setPreviewError('camera');
            }
            if (micEnabled && (blockedAudio || !blockedVideo)) {
                onMicChange(false);
                setPreviewError((prev) => prev ?? 'mic');
            }
        },
    );

    const videoTrack = useMemo(() => {
        if (!previewTracks) return undefined;
        return previewTracks.find(
            (track): track is LocalVideoTrack =>
                track.kind === Track.Kind.Video,
        );
    }, [previewTracks]);

    useEffect(() => {
        const element = videoRef.current;
        if (!element) return;
        if (!videoTrack) return;
        videoTrack.attach(element);
        return () => {
            videoTrack.detach(element);
        };
    }, [videoTrack]);

    useEffect(() => {
        if (!micEnabled || !videoEnabled) return;
        if (previewError) {
            setPreviewError(null);
        }
    }, [micEnabled, videoEnabled, previewError]);

    useEffect(() => {
        if (!previewTracks) return;
        return () => {
            for (const track of previewTracks) {
                track.stop();
            }
        };
    }, [previewTracks]);

    const isNeedsPassword = state.phase === 'NEEDS_PASSWORD';
    const isLoading =
        state.phase === 'LOOKING_UP'
        || state.phase === 'REQUESTING'
        || isValidatingToken;

    const initialStepSchema = useMemo(
        () => buildInitialStepSchema(t('validation.required')),
        [t],
    );

    const passwordStepSchema = useMemo(
        () => buildPasswordStepSchema(t('validation.required')),
        [t],
    );

    const initialForm = useForm<InitialStepValues>({
        resolver: zodResolver(initialStepSchema),
        defaultValues: {
            code: initialCode,
            displayName: initialDisplayName,
        },
    });

    const passwordForm = useForm<PasswordStepValues>({
        resolver: zodResolver(passwordStepSchema),
        defaultValues: {
            displayName: initialDisplayName,
            password: '',
        },
    });

    useEffect(() => {
        if (initialDisplayName) {
            initialForm.setValue('displayName', initialDisplayName);
            passwordForm.setValue('displayName', initialDisplayName);
        }
    }, [initialDisplayName, initialForm, passwordForm]);

    useEffect(() => {
        if (initialCode) {
            initialForm.setValue('code', initialCode);
        }
    }, [initialCode, initialForm]);

    const passwordError =
        state.phase === 'NEEDS_PASSWORD' && state.error === 'INVALID_PASSWORD'
            ? t('errors.invalidPassword')
            : undefined;

    function handleInitialSubmit(values: InitialStepValues) {
        onSubmit({
            code: values.code.trim(),
            displayName: values.displayName.trim(),
            password: undefined,
        });
    }

    function handlePasswordSubmit(values: PasswordStepValues) {
        onSubmitPassword({
            displayName: values.displayName,
            password: values.password,
        });
    }

    function handleToggleMic() {
        if (micEnabled) {
            onMicChange(false);
            return;
        }
        setPreviewError(null);
        onMicChange(true);
    }

    function handleToggleVideo() {
        if (videoEnabled) {
            onVideoChange(false);
            return;
        }
        setPreviewError(null);
        onVideoChange(true);
    }

    const previewMessage =
        previewError === 'camera'
            ? t('permissionDeniedCamera')
            : previewError === 'mic'
              ? t('permissionDeniedMic')
              : null;

    return (
        <div className='flex flex-col gap-6'>
            <div className='relative aspect-[1.6] w-full overflow-hidden rounded-[1.7rem] bg-[linear-gradient(135deg,_#111827_0%,_#2b313b_32%,_#111827_100%)] shadow-[0_26px_70px_-38px_rgba(15,23,42,0.35)]'>
                {videoEnabled && videoTrack ? (
                    <video
                        autoPlay
                        className='absolute inset-0 h-full w-full object-cover'
                        muted
                        playsInline
                        ref={videoRef}
                    />
                ) : (
                    <div className='absolute inset-0 bg-[radial-gradient(circle_at_40%_50%,_rgba(255,213,128,0.12),_transparent_28%),linear-gradient(90deg,_rgba(0,0,0,0.52)_0%,_rgba(0,0,0,0.12)_45%,_rgba(0,0,0,0.62)_100%)]' />
                )}

                <span className='absolute left-6 top-6 rounded-2xl bg-black/38 px-4 py-1.5 text-[0.95rem] font-medium text-white backdrop-blur'>
                    {t('preview')}
                </span>

                {previewMessage && (
                    <div
                        className='absolute left-1/2 top-6 -translate-x-1/2 rounded-2xl bg-error/90 px-4 py-2 text-sm font-medium text-white shadow-[0_18px_50px_-30px_rgba(0,0,0,0.8)] backdrop-blur'
                        role='alert'
                    >
                        {previewMessage}
                    </div>
                )}

                <div className='absolute bottom-6 left-1/2 flex -translate-x-1/2 items-center gap-4 rounded-[2rem] bg-white/88 px-6 py-4 shadow-[0_24px_60px_-36px_rgba(15,23,42,0.55)] backdrop-blur'>
                    <button
                        aria-label={micEnabled ? t('muteMic') : t('unmuteMic')}
                        className={`flex min-w-[78px] flex-col items-center gap-1.5 rounded-2xl px-2.5 py-2 text-text-primary transition-colors ${
                            micEnabled
                                ? 'bg-surface-input'
                                : 'bg-error-subtle text-error-dark'
                        }`}
                        onClick={handleToggleMic}
                        type='button'
                    >
                        <span className='flex h-12 w-12 items-center justify-center rounded-full bg-white/90 shadow-sm'>
                            {micEnabled ? (
                                <Mic className='h-7 w-7' />
                            ) : (
                                <MicOff className='h-7 w-7' />
                            )}
                        </span>
                        <span className='text-[0.8rem] font-medium uppercase tracking-[0.12em]'>
                            {t('mic')}
                        </span>
                    </button>

                    <button
                        aria-label={
                            videoEnabled ? t('stopVideo') : t('startVideo')
                        }
                        className={`flex min-w-[78px] flex-col items-center gap-1.5 rounded-2xl px-2.5 py-2 text-text-primary transition-colors ${
                            videoEnabled
                                ? 'bg-surface-input'
                                : 'bg-error-subtle text-error-dark'
                        }`}
                        onClick={handleToggleVideo}
                        type='button'
                    >
                        <span className='flex h-12 w-12 items-center justify-center rounded-full bg-white/90 shadow-sm'>
                            {videoEnabled ? (
                                <Video className='h-7 w-7' />
                            ) : (
                                <VideoOff className='h-7 w-7' />
                            )}
                        </span>
                        <span className='text-[0.8rem] font-medium uppercase tracking-[0.12em]'>
                            {t('video')}
                        </span>
                    </button>
                </div>
            </div>

            {isNeedsPassword ? (
                <form
                    className='flex flex-col gap-4'
                    onSubmit={passwordForm.handleSubmit(handlePasswordSubmit)}
                >
                    <p className='text-base text-text-secondary'>
                        {t('passwordRequired')}
                    </p>
                    <div className='flex flex-col gap-1'>
                        <label
                            className='text-sm font-medium text-text-dark'
                            htmlFor='join-password'
                        >
                            {t('passwordLabel')}
                        </label>
                        <input
                            autoComplete='current-password'
                            className='h-12 w-full rounded-xl border border-border-input bg-surface px-4 text-text-darkest outline-none ring-transparent transition focus:ring-2 focus:ring-primary'
                            id='join-password'
                            placeholder={t('passwordPlaceholder')}
                            type='password'
                            {...passwordForm.register('password')}
                        />
                        {passwordError && (
                            <p className='text-sm text-error-dark' role='alert'>
                                {passwordError}
                            </p>
                        )}
                        {passwordForm.formState.errors.password && (
                            <p className='text-sm text-error-dark' role='alert'>
                                {passwordForm.formState.errors.password.message}
                            </p>
                        )}
                    </div>
                    <Button
                        className='h-14 w-full rounded-xl text-base font-semibold'
                        disabled={isLoading}
                        type='submit'
                    >
                        {isLoading ? t('joining') : t('submitPassword')}
                    </Button>
                </form>
            ) : (
                <form
                    className='flex flex-col gap-4'
                    onSubmit={initialForm.handleSubmit(handleInitialSubmit)}
                >
                    <div className='flex flex-col gap-1'>
                        <label
                            className='text-sm font-medium text-text-dark'
                            htmlFor='join-code'
                        >
                            {t('codeLabel')}
                        </label>
                        <input
                            autoComplete='off'
                            className='h-12 w-full rounded-xl border border-border-input bg-surface px-4 text-text-darkest outline-none ring-transparent transition focus:ring-2 focus:ring-primary disabled:opacity-50'
                            disabled={isValidatingToken}
                            id='join-code'
                            placeholder={t('codePlaceholder')}
                            type='text'
                            {...initialForm.register('code', {
                                onChange: () => onCodeChange?.(),
                            })}
                        />
                        {isValidatingToken && (
                            <p className='text-xs text-text-subtle'>
                                {t('tokenValidating')}
                            </p>
                        )}
                        {tokenError && (
                            <p className='text-sm text-error-dark' role='alert'>
                                {tokenError}
                            </p>
                        )}
                        {!tokenError && initialForm.formState.errors.code && (
                            <p className='text-sm text-error-dark' role='alert'>
                                {initialForm.formState.errors.code.message}
                            </p>
                        )}
                    </div>

                    {(mode === 'guest' || !initialDisplayName) && (
                        <div className='flex flex-col gap-1'>
                            <label
                                className='text-sm font-medium text-text-dark'
                                htmlFor='join-display-name'
                            >
                                {t('displayNameLabel')}
                            </label>
                            <input
                                autoComplete='nickname'
                                className='h-12 w-full rounded-xl border border-border-input bg-surface px-4 text-text-darkest outline-none ring-transparent transition focus:ring-2 focus:ring-primary'
                                id='join-display-name'
                                placeholder={t('displayNamePlaceholder')}
                                type='text'
                                {...initialForm.register('displayName')}
                            />
                            {initialForm.formState.errors.displayName && (
                                <p
                                    className='text-sm text-error-dark'
                                    role='alert'
                                >
                                    {
                                        initialForm.formState.errors.displayName
                                            .message
                                    }
                                </p>
                            )}
                        </div>
                    )}

                    <Button
                        className='h-14 w-full rounded-xl text-base font-semibold'
                        disabled={isLoading}
                        type='submit'
                    >
                        {isLoading ? t('joining') : t('join')}
                    </Button>
                </form>
            )}
        </div>
    );
}
