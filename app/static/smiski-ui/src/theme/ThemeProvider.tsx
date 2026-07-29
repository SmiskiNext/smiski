import { type ReactNode, useEffect, useState } from 'react';

export type AppColorMode = 'light' | 'dark' | 'auto';
export type ResolvedColorMode = 'light' | 'dark';

function resolveColorMode(
    colorMode: AppColorMode | null | undefined,
    prefersDark: boolean,
): ResolvedColorMode {
    if (colorMode === 'dark') return 'dark';
    if (colorMode === 'light') return 'light';
    return prefersDark ? 'dark' : 'light';
}

/**
 * Resolves the app's color mode to a concrete `light`/`dark` value, following
 * the OS preference when the mode is `auto`. Shared so Ant Design's
 * ConfigProvider and the CSS `data-theme` attribute stay in sync.
 */
export function useResolvedColorMode(
    colorMode?: AppColorMode | null,
): ResolvedColorMode {
    const [resolved, setResolved] = useState<ResolvedColorMode>(() =>
        resolveColorMode(
            colorMode,
            typeof window !== 'undefined'
                && window.matchMedia('(prefers-color-scheme: dark)').matches,
        ),
    );

    useEffect(() => {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const update = () =>
            setResolved(resolveColorMode(colorMode, media.matches));
        update();
        media.addEventListener('change', update);
        return () => media.removeEventListener('change', update);
    }, [colorMode]);

    return resolved;
}

export interface ThemeProviderProps {
    colorMode?: AppColorMode | null;
    children: ReactNode;
}

export function ThemeProvider({ colorMode, children }: ThemeProviderProps) {
    const resolved = useResolvedColorMode(colorMode);

    useEffect(() => {
        document.documentElement.dataset.theme = resolved;
    }, [resolved]);

    return <>{children}</>;
}
