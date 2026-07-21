import { useLayoutEffect, useState, type RefObject } from 'react';

export interface FloatingPosition {
  top: number;
  left: number;
  width: number;
}

/**
 * Tracks the viewport-relative position of a trigger element while a portaled
 * popover (dropdown menu, etc.) is open, so the popover can render with
 * `position: fixed` in `document.body` — escaping any scrollable ancestor
 * instead of being clipped by / expanding its scrollHeight (an `absolute`
 * popover nested inside an `overflow-y-auto` container still contributes to
 * that container's scrollable overflow even though it visually floats above
 * sibling content).
 */
export function useFloatingPosition(
  triggerRef: RefObject<HTMLElement | null>,
  isOpen: boolean,
): FloatingPosition | null {
  const [position, setPosition] = useState<FloatingPosition | null>(null);

  useLayoutEffect(() => {
    if (!isOpen) {
      setPosition(null);
      return;
    }
    const update = () => {
      const rect = triggerRef.current?.getBoundingClientRect();
      if (!rect) return;
      setPosition({ top: rect.bottom + 4, left: rect.left, width: rect.width });
    };
    update();
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
    };
  }, [isOpen, triggerRef]);

  return position;
}
