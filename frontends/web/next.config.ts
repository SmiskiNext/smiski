import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin();

const nextConfig: NextConfig = {
    output: 'standalone',
    productionBrowserSourceMaps: false,
    reactCompiler: true,
    turbopack: {
        resolveExtensions: [
            '.ts',
            '.tsx',
            '.js',
            '.jsx',
            '.json',
            '.cjs',
            '.mjs',
            '.css',
        ],
    },
};

export default withNextIntl(nextConfig);
