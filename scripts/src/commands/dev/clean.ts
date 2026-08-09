import { defineCommand } from 'citty';
import { consola } from 'consola';
import { DEV_STACK, runCompose } from '../../lib/docker.ts';
import { isInteractive } from '../../lib/prompt.ts';

export const cleanCommand = defineCommand({
    meta: {
        name: 'clean',
        description:
            'Stop the development stack and remove its volumes, orphans and network',
    },
    args: {
        force: {
            type: 'boolean',
            description: 'Skip the confirmation prompt',
            default: false,
        },
    },
    async run({ args }) {
        consola.warn(
            'This deletes every data volume for the development stack: '
                + 'Postgres, Kafka, Valkey and LiveKit Redis all lose their data. '
                + 'This cannot be undone.',
        );

        if (!args.force) {
            if (!isInteractive()) {
                consola.error(
                    'Refusing to delete volumes without confirmation. There is '
                        + 'no terminal to confirm on, so pass --force to proceed.',
                );
                process.exitCode = 1;
                return;
            }

            const confirmed = await consola.prompt(
                'Continue and delete the volumes?',
                { type: 'confirm', initial: false },
            );

            if (confirmed !== true) {
                consola.info('Aborted — no volumes were removed.');
                return;
            }
        }

        const exitCode = await runCompose(DEV_STACK, [
            'down',
            '-v',
            '--remove-orphans',
        ]);

        process.exitCode = exitCode;
    },
});
