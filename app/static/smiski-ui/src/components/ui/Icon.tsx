import type { LucideIcon } from 'lucide-react';
import {
    AlertTriangle,
    Calendar,
    Check,
    ChevronDown,
    ChevronUp,
    Circle,
    Clock,
    ExternalLink,
    History,
    Info,
    Mic,
    MicOff,
    MoreHorizontal,
    Pencil,
    PhoneOff,
    Play,
    Plus,
    ScreenShare,
    Search,
    Sparkle,
    Square,
    Trash2,
    Users,
    Video,
    VideoOff,
    X,
} from 'lucide-react';
import type { SVGProps } from 'react';

export type IconName =
    | 'alert'
    | 'calendar'
    | 'camera'
    | 'cameraOff'
    | 'check'
    | 'chevronDown'
    | 'chevronUp'
    | 'clock'
    | 'edit'
    | 'external'
    | 'history'
    | 'info'
    | 'mic'
    | 'micOff'
    | 'more'
    | 'people'
    | 'phoneOff'
    | 'play'
    | 'plus'
    | 'record'
    | 'search'
    | 'screen'
    | 'spark'
    | 'stop'
    | 'trash'
    | 'video'
    | 'x';

const icons: Record<IconName, LucideIcon> = {
    alert: AlertTriangle,
    calendar: Calendar,
    camera: Video,
    cameraOff: VideoOff,
    check: Check,
    chevronDown: ChevronDown,
    chevronUp: ChevronUp,
    clock: Clock,
    edit: Pencil,
    external: ExternalLink,
    history: History,
    info: Info,
    mic: Mic,
    micOff: MicOff,
    more: MoreHorizontal,
    people: Users,
    phoneOff: PhoneOff,
    play: Play,
    plus: Plus,
    record: Circle,
    search: Search,
    screen: ScreenShare,
    spark: Sparkle,
    stop: Square,
    trash: Trash2,
    video: Video,
    x: X,
};

/** Names rendered filled (matching a solid-dot record indicator / stop button) rather than outlined. */
const filled = new Set<IconName>(['record', 'stop']);

export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
    name: IconName;
    size?: number;
}

export function Icon({ name, size = 18, ...props }: IconProps) {
    const Component = icons[name];
    return (
        <Component
            aria-hidden={props['aria-label'] ? undefined : true}
            size={size}
            strokeWidth={1.8}
            fill={filled.has(name) ? 'currentColor' : 'none'}
            {...props}
        />
    );
}
