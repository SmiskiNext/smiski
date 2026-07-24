import { type ReactNode, useEffect } from 'react';

export type AppColorMode = 'light' | 'dark' | 'auto';

export interface ThemeProviderProps {
    colorMode?: AppColorMode | null;
    children: ReactNode;
}

export function ThemeProvider({ colorMode, children }: ThemeProviderProps) {
    useEffect(() => {
        const root = document.documentElement;
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const applyTheme = () => {
            const resolved =
                colorMode === 'dark' || (colorMode !== 'light' && media.matches)
                    ? 'dark'
                    : 'light';
            root.dataset.theme = resolved;
        };

        applyTheme();
        media.addEventListener('change', applyTheme);
        return () => media.removeEventListener('change', applyTheme);
    }, [colorMode]);

    return <>{children}</>;
}
