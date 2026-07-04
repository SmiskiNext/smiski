'use client';

import { useEffect, useState } from 'react';

type WindowSize = {
    width: number;
    height: number;
};

/**
 * Returns the current viewport width and height, updating on resize.
 * Defaults to 1024x768 during SSR to avoid hydration mismatches.
 */
export function useWindowSize(): WindowSize {
    const [size, setSize] = useState<WindowSize>({
        width: typeof window !== 'undefined' ? window.innerWidth : 1024,
        height: typeof window !== 'undefined' ? window.innerHeight : 768,
    });

    useEffect(() => {
        function handleResize() {
            setSize({
                width: window.innerWidth,
                height: window.innerHeight,
            });
        }

        window.addEventListener('resize', handleResize);
        handleResize();

        return () => window.removeEventListener('resize', handleResize);
    }, []);

    return size;
}
