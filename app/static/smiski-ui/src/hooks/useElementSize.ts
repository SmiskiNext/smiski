/**
 * useElementSize — tracks an element's content-box size via ResizeObserver.
 * Used by layouts that need actual pixel dimensions to size children (e.g.
 * fitting fixed-aspect-ratio tiles into an adaptive grid).
 */
import { useEffect, useRef, useState } from 'react';

export function useElementSize<T extends HTMLElement>() {
    const ref = useRef<T>(null);
    const [size, setSize] = useState({ width: 0, height: 0 });

    useEffect(() => {
        const el = ref.current;
        if (!el) return;
        const observer = new ResizeObserver(([entry]) => {
            const { width, height } = entry.contentRect;
            setSize({ width, height });
        });
        observer.observe(el);
        return () => observer.disconnect();
    }, []);

    return [ref, size] as const;
}
