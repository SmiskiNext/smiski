import { redirect } from 'next/navigation';
import { routing } from '@/i18n/request.ts';

export default function RootPage() {
    redirect(`/${routing.defaultLocale}/home`);
}
