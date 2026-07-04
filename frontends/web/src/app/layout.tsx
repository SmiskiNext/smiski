import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import { ThemeProvider } from '@/components/shared/theme-provider.tsx';
import { Toaster } from '@/components/ui/sonner.tsx';
import './globals.css';

const geistSans = Geist({
    variable: '--font-geist-sans',
    subsets: ['latin'],
});

const geistMono = Geist_Mono({
    variable: '--font-geist-mono',
    subsets: ['latin'],
});

export const metadata: Metadata = {
    title: 'Zero Meeting System',
    description: 'A modern meeting management platform',
    applicationName: 'Zero Meet',
    icons: {
        icon: [
            { url: '/icon.svg', type: 'image/svg+xml' },
            { url: '/favicon.ico', sizes: '48x48', type: 'image/x-icon' },
        ],
        apple: { url: '/apple-touch-icon.png', sizes: '180x180' },
    },
    openGraph: {
        title: 'Zero Meet',
        description: 'A modern meeting management platform',
        siteName: 'Zero Meet',
        images: [
            {
                url: '/og-image.png',
                width: 1200,
                height: 630,
                alt: 'Zero Meet',
            },
        ],
        type: 'website',
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Zero Meet',
        description: 'A modern meeting management platform',
        images: ['/og-image.png'],
    },
};

export const viewport = {
    themeColor: '#1a73e8',
};

export default function RootLayout({
    children,
}: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang='en' suppressHydrationWarning>
            <body
                className={`${geistSans.variable} ${geistMono.variable} antialiased`}
            >
                <ThemeProvider>
                    {children}
                    <Toaster />
                </ThemeProvider>
            </body>
        </html>
    );
}
