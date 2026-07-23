import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
    input: '../../services/openapi.yaml',
    output: {
        path: 'src/generated',
        clean: true,
        importFileExtension: '.js',
        tsConfigPath: 'tsconfig.json',
    },
    plugins: [
        {
            name: '@hey-api/typescript',
        },
        {
            name: '@hey-api/sdk',
            auth: true,
            validator: {
                request: 'zod',
                response: 'zod',
            },
        },
        {
            name: 'zod',
            compatibilityVersion: 4,
            exportFromIndex: true,
        },
        {
            name: '@hey-api/client-fetch',
        },
    ],
});
