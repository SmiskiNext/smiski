import { Suspense } from 'react';
import { AuthContainer } from '@/components/auth/index.tsx';

export default function RegisterPage() {
    return (
        <Suspense fallback={null}>
            <AuthContainer variant='register' />
        </Suspense>
    );
}
