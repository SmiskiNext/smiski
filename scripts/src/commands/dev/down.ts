import { defineCommand } from 'citty';
import { DEV_STACK, runCompose } from '../../lib/docker.ts';

export const downCommand = defineCommand({
    meta: {
        name: 'down',
        description: 'Stop the development stack, keeping its data volumes',
    },
    async run() {
        const exitCode = await runCompose(DEV_STACK, ['down']);

        if (exitCode !== 0) {
            process.exitCode = exitCode;
            return;
        }

        console.log(
            '\nData volumes kept — databases, Kafka topics and cache survive. '
                + 'Use smiski dev clean to remove them.',
        );
    },
});
