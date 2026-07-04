'use client';

import { Loader2 } from 'lucide-react';

function GoogleBrandIcon() {
    return (
        <svg aria-hidden='true' className='h-6 w-6' viewBox='0 0 24 24'>
            <path
                d='M21.8 12.23c0-.77-.07-1.5-.2-2.2H12v4.16h5.48a4.7 4.7 0 0 1-2.03 3.08v2.56h3.3c1.94-1.79 3.05-4.42 3.05-7.6Z'
                fill='#4285F4'
            />
            <path
                d='M12 22c2.76 0 5.08-.91 6.77-2.47l-3.3-2.56c-.92.62-2.08.98-3.47.98-2.66 0-4.92-1.8-5.73-4.21H2.86v2.63A10.22 10.22 0 0 0 12 22Z'
                fill='#34A853'
            />
            <path
                d='M6.27 13.74A6.12 6.12 0 0 1 5.95 12c0-.6.1-1.18.32-1.74V7.63H2.86a10.05 10.05 0 0 0 0 8.74l3.41-2.63Z'
                fill='#FBBC04'
            />
            <path
                d='M12 6.05c1.5 0 2.84.52 3.89 1.52l2.92-2.92C17.07 2.98 14.76 2 12 2 7.99 2 4.5 4.3 2.86 7.63l3.41 2.63c.8-2.41 3.07-4.21 5.73-4.21Z'
                fill='#EA4335'
            />
        </svg>
    );
}

type GoogleSignInButtonProps = {
    label: string;
    loading: boolean;
    disabled: boolean;
    onClick: () => void;
};

export function GoogleSignInButton({
    label,
    loading,
    disabled,
    onClick,
}: GoogleSignInButtonProps) {
    return (
        <button
            className='mt-9 flex h-16 w-full items-center justify-center gap-4 rounded-full border border-border-input bg-surface px-6 text-[1.15rem] font-medium text-text-primary transition-colors hover:bg-surface-pale-blue disabled:opacity-60'
            disabled={disabled}
            onClick={onClick}
            type='button'
        >
            {loading ? (
                <Loader2 className='h-5 w-5 animate-spin' />
            ) : (
                <>
                    <GoogleBrandIcon />
                    {label}
                </>
            )}
        </button>
    );
}
