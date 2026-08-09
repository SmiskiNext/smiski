import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useCurrentUser } from '../../context/CurrentUserContext';
import {
    getAvailableMeetingActions,
    type Meeting,
    type MeetingAction,
    type MeetingPermissions,
} from '../../domain';
import { Button, Icon, type IconName } from '../ui';
import { actionLabel } from './meetingActionRules';

interface MenuPosition {
    top: number;
    left: number;
}

export interface MeetingActionMenuProps {
    meetingId: string;
    meeting: Meeting;
    permissions: MeetingPermissions;
    hiddenActions?: MeetingAction[];
    onAction: (action: MeetingAction, meetingId: string) => void;
}

const ACTION_ICONS: Record<MeetingAction, IconName> = {
    EDIT: 'edit',
    CANCEL: 'trash',
    END: 'phoneOff',
    START: 'play',
    JOIN: 'video',
    VIEW_DETAIL: 'info',
    VIEW_HISTORY: 'history',
    SETTINGS: 'settings',
    DELETE: 'trash',
};

export function MeetingActionMenu({
    meetingId,
    meeting,
    permissions,
    hiddenActions = [],
    onAction,
}: MeetingActionMenuProps) {
    const [isOpen, setOpen] = useState(false);
    const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null);
    const rootRef = useRef<HTMLDivElement>(null);
    const menuRef = useRef<HTMLDivElement>(null);
    const currentUser = useCurrentUser();
    const actions = getAvailableMeetingActions(
        meeting,
        permissions,
        currentUser.accountId,
    ).filter((action) => !hiddenActions.includes(action));

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

    // Two-pass: mount the (hidden) menu first so its real size can be measured,
    // then flip it above the trigger when there isn't room below — avoids the
    // absolute-positioned-inside-a-table clipping/overlap near the viewport edge.
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
            const margin = 4;
            const spaceBelow = window.innerHeight - triggerRect.bottom;
            const openUp =
                spaceBelow < menuHeight + margin
                && triggerRect.top > menuHeight + margin;
            setMenuPosition({
                top: openUp
                    ? triggerRect.top - menuHeight - margin
                    : triggerRect.bottom + margin,
                left: Math.min(
                    Math.max(triggerRect.right - menuWidth, 4),
                    window.innerWidth - menuWidth - 4,
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

    if (actions.length === 0) return null;

    return (
        <div ref={rootRef} className='relative'>
            <Button
                size='icon'
                variant='ghost'
                className='size-7 min-h-0 rounded-md'
                aria-label='Meeting actions'
                aria-expanded={isOpen}
                onClick={(event) => {
                    event.stopPropagation();
                    setOpen((open) => !open);
                }}
            >
                <Icon name='more' />
            </Button>
            {isOpen
                && createPortal(
                    <div
                        ref={menuRef}
                        style={{
                            position: 'fixed',
                            top: menuPosition?.top ?? -9999,
                            left: menuPosition?.left ?? -9999,
                            visibility: menuPosition ? 'visible' : 'hidden',
                        }}
                        className='z-30 min-w-44 overflow-hidden rounded-md border bg-[var(--surface)] p-1 shadow-panel'
                    >
                        {actions.map((action) => (
                            <button
                                key={action}
                                type='button'
                                className={`flex w-full items-center gap-2.5 rounded px-2.5 py-1.5 text-left text-xs font-medium transition hover:bg-[var(--surface-soft)] ${action === 'CANCEL' || action === 'END' || action === 'DELETE' ? 'text-red-600 dark:text-red-300' : 'text-[var(--text)]'}`}
                                onClick={(event) => {
                                    event.stopPropagation();
                                    setOpen(false);
                                    onAction(action, meetingId);
                                }}
                            >
                                <Icon name={ACTION_ICONS[action]} size={16} />
                                {actionLabel(action)}
                            </button>
                        ))}
                    </div>,
                    document.body,
                )}
        </div>
    );
}
