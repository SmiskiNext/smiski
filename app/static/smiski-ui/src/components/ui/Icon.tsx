import type { LucideIcon } from 'lucide-react';
import {
    AlertTriangle,
    Bell,
    BellOff,
    Calendar,
    Check,
    ChevronDown,
    ChevronUp,
    Circle,
    Clock,
    ExternalLink,
    History,
    Info,
    LayoutGrid,
    Mic,
    MicOff,
    MoreHorizontal,
    Pencil,
    PhoneOff,
    Pin,
    PinOff,
    Play,
    Plus,
    ScreenShare,
    Search,
    Settings,
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
    | 'bell'
    | 'bellOff'
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
    | 'layout'
    | 'mic'
    | 'micOff'
    | 'more'
    | 'people'
    | 'phoneOff'
    | 'pin'
    | 'pinOff'
    | 'play'
    | 'plus'
    | 'record'
    | 'search'
    | 'screen'
    | 'settings'
    | 'spark'
    | 'stop'
    | 'trash'
    | 'video'
    | 'x';

const icons: Record<IconName, LucideIcon> = {
    alert: AlertTriangle,
    bell: Bell,
    bellOff: BellOff,
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
    layout: LayoutGrid,
    mic: Mic,
    micOff: MicOff,
    more: MoreHorizontal,
    people: Users,
    phoneOff: PhoneOff,
    pin: Pin,
    pinOff: PinOff,
    play: Play,
    plus: Plus,
    record: Circle,
    search: Search,
    screen: ScreenShare,
    settings: Settings,
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
