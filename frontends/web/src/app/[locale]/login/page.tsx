import { Suspense } from 'react';
import { AuthContainer } from '@/components/auth/index.tsx';

export default function LoginPage() {
    return (
        <Suspense fallback={null}>
            <AuthContainer variant='login' />
        </Suspense>
    );
}
