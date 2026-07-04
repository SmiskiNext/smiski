import type { SVGProps } from 'react';

type LogoProps = Omit<
    SVGProps<SVGSVGElement>,
    'aria-label' | 'aria-labelledby'
> & {
    label?: string;
    decorative?: boolean;
};

export function Logo({
    label = 'Zero Meet',
    decorative = false,
    ...props
}: LogoProps) {
    return (
        <svg
            aria-hidden={decorative ? true : undefined}
            aria-label={decorative ? undefined : label}
            fill='none'
            role={decorative ? 'presentation' : 'img'}
            viewBox='0 0 64 64'
            xmlns='http://www.w3.org/2000/svg'
            {...props}
        >
            <path
                d='M20 8h18c7.732 0 14 6.268 14 14v12 M52 42v2c0 7.732-6.268 14-14 14H20c-7.732 0-14-6.268-14-14V22c0-7.732 6.268-14 14-14'
                fill='none'
                stroke='currentColor'
                strokeLinecap='round'
                strokeLinejoin='round'
                strokeWidth={5}
            />
            <circle cx={32} cy={34} fill='currentColor' r={5} />
        </svg>
    );
}

type LogoLockupProps = {
    className?: string;
    markClassName?: string;
    wordmarkClassName?: string;
    label?: string;
    showWordmark?: boolean;
};

export function LogoLockup({
    className,
    markClassName = 'h-8 w-8 text-primary',
    wordmarkClassName = 'text-[1.25rem] font-semibold tracking-tight text-primary',
    label = 'Zero Meet',
    showWordmark = true,
}: LogoLockupProps) {
    return (
        <span className={className}>
            <Logo
                className={markClassName}
                decorative={showWordmark}
                label={label}
            />
            {showWordmark ? (
                <span className={wordmarkClassName}>{label}</span>
            ) : null}
        </span>
    );
}
