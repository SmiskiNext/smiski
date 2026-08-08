/**
 * MeetingLayoutMenu — the meeting room's "Layout" popover, offering the three
 * arrangements `resolveMeetingLayout` understands.
 *
 * Portals into `document.body` and positions itself with `position: fixed`,
 * matching `MeetingActionMenu`: the room footer sits inside a rounded,
 * `overflow-hidden` section, which would clip a menu opening upward out of it.
 * Positioning is measured after mount so the menu can be placed above the
 * trigger (there is never room below a footer control).
 */
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { cn, Icon } from '../../../components/ui';
import { LAYOUT_MODES, type LayoutMode } from '../../../domain';

interface MenuPosition {
    top: number;
    left: number;
}

const MODE_LABELS: Record<LayoutMode, string> = {
    auto: 'Auto',
    tiled: 'Tiled',
    spotlight: 'Spotlight',
};

const MODE_DESCRIPTIONS: Record<LayoutMode, string> = {
    auto: 'Follow the room',
    tiled: 'Everyone the same size',
    spotlight: 'One large, rest small',
};

export interface MeetingLayoutMenuProps {
    mode: LayoutMode;
    onModeChange: (mode: LayoutMode) => void;
}

export function MeetingLayoutMenu({
    mode,
    onModeChange,
}: MeetingLayoutMenuProps) {
    const [isOpen, setOpen] = useState(false);
    const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null);
    const rootRef = useRef<HTMLDivElement>(null);
    const menuRef = useRef<HTMLDivElement>(null);
    const listboxId = useId();

    useEffect(() => {
        if (!isOpen) return;
        const close = (event: MouseEvent) => {
            const target = event.target as Node;
            if (
                rootRef.current?.contains(target)
                || menuRef.current?.contains(target)
            )
                return;
            setOpen(false);
        };
        const closeOnEscape = (event: KeyboardEvent) =>
            event.key === 'Escape' && setOpen(false);
        document.addEventListener('mousedown', close);
        document.addEventListener('keydown', closeOnEscape);
        return () => {
            document.removeEventListener('mousedown', close);
            document.removeEventListener('keydown', closeOnEscape);
        };
    }, [isOpen]);

    useLayoutEffect(() => {
        if (!isOpen) {
            setMenuPosition(null);
            return;
        }
        const update = () => {
            const triggerRect = rootRef.current?.getBoundingClientRect();
            if (!triggerRect || !menuRef.current) return;
            const menuHeight = menuRef.current.offsetHeight;
            const menuWidth = menuRef.current.offsetWidth;
            const margin = 8;
            const spaceAbove = triggerRect.top;
            setMenuPosition({
                top:
                    spaceAbove > menuHeight + margin
                        ? triggerRect.top - menuHeight - margin
                        : triggerRect.bottom + margin,
                left: Math.min(
                    Math.max(
                        triggerRect.left
                            + triggerRect.width / 2
                            - menuWidth / 2,
                        margin,
                    ),
                    Math.max(window.innerWidth - menuWidth - margin, margin),
                ),
            });
        };
        update();
        window.addEventListener('scroll', update, true);
        window.addEventListener('resize', update);
        return () => {
            window.removeEventListener('scroll', update, true);
            window.removeEventListener('resize', update);
        };
    }, [isOpen]);

    return (
        <div ref={rootRef} className='relative'>
            <button
                type='button'
                aria-label='Layout'
                aria-haspopup='listbox'
                aria-expanded={isOpen}
                aria-controls={listboxId}
                onClick={() => setOpen((open) => !open)}
                className={cn(
                    'group flex min-w-16 flex-col items-center gap-1.5 text-[10px] font-semibold text-slate-300 transition',
                )}
            >
                <span
                    className={cn(
                        'relative flex size-10 items-center justify-center rounded-full border transition sm:size-11',
                        isOpen
                            ? 'border-white/10 bg-white/10 text-white hover:bg-white/20'
                            : 'border-white/5 bg-white/5 text-slate-400 hover:bg-white/10',
                    )}
                >
                    <Icon name='layout' size={18} />
                </span>
                <span className='hidden sm:block'>Layout</span>
            </button>
            {isOpen
                && createPortal(
                    <div
                        ref={menuRef}
                        id={listboxId}
                        role='listbox'
                        aria-label='Layout'
                        style={{
                            position: 'fixed',
                            top: menuPosition?.top ?? -9999,
                            left: menuPosition?.left ?? -9999,
                            visibility: menuPosition ? 'visible' : 'hidden',
                        }}
                        className='z-40 min-w-52 overflow-hidden rounded-xl border border-white/10 bg-slate-900 p-1 shadow-panel'
                    >
                        {LAYOUT_MODES.map((option) => {
                            const isSelected = option === mode;
                            return (
                                <button
                                    key={option}
                                    type='button'
                                    role='option'
                                    aria-selected={isSelected}
                                    onClick={() => {
                                        setOpen(false);
                                        onModeChange(option);
                                    }}
                                    className={cn(
                                        'flex w-full items-center justify-between gap-3 rounded-lg px-2.5 py-2 text-left transition',
                                        isSelected
                                            ? 'bg-white/10 text-white'
                                            : 'text-slate-300 hover:bg-white/5',
                                    )}
                                >
                                    <span className='min-w-0'>
                                        <span className='block text-xs font-semibold'>
                                            {MODE_LABELS[option]}
                                        </span>
                                        <span className='block text-[10px] text-slate-400'>
                                            {MODE_DESCRIPTIONS[option]}
                                        </span>
                                    </span>
                                    {isSelected && (
                                        <Icon
                                            name='check'
                                            size={14}
                                            className='shrink-0'
                                        />
                                    )}
                                </button>
                            );
                        })}
                    </div>,
                    document.body,
                )}
        </div>
    );
}
