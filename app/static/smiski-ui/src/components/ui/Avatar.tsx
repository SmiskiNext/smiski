import { cn } from './cn';

const palette = [
    'bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-200',
    'bg-violet-100 text-violet-700 dark:bg-violet-500/20 dark:text-violet-200',
    'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-200',
    'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-200',
    'bg-rose-100 text-rose-700 dark:bg-rose-500/20 dark:text-rose-200',
];

function initials(name: string): string {
    return name
        .trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part[0])
        .join('')
        .toUpperCase();
}

function colorFor(name: string): string {
    const hash = [...name].reduce(
        (sum, character) => sum + character.charCodeAt(0),
        0,
    );
    return palette[hash % palette.length];
}

export interface AvatarProps {
    name: string;
    src?: string;
    size?: 'sm' | 'md' | 'lg' | 'xl';
    className?: string;
}

const sizes = {
    sm: 'size-8 text-[10px]',
    md: 'size-10 text-xs',
    lg: 'size-14 text-base',
    xl: 'size-20 text-xl',
};

export function Avatar({ name, src, size = 'md', className }: AvatarProps) {
    return src ? (
        <img
            src={src}
            alt={name}
            className={cn(
                'rounded-full object-cover ring-2 ring-[var(--surface)]',
                sizes[size],
                className,
            )}
        />
    ) : (
        <span
            title={name}
            role='img'
            aria-label={name}
            className={cn(
                'inline-flex shrink-0 items-center justify-center rounded-full font-bold ring-2 ring-[var(--surface)]',
                colorFor(name),
                sizes[size],
                className,
            )}
        >
            {initials(name)}
        </span>
    );
}
