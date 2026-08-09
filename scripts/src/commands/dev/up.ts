import { defineCommand } from 'citty';
import { $ } from 'zx';
import { DEV_STACK, runCompose } from '../../lib/docker.ts';
import { repositoryRoot } from '../../lib/paths.ts';

/**
 * Services whose images compose pulls rather than builds.
 *
 * Compose has no build context for these three, so a missing or stale image is
 * not something `up` can correct — it fails with a pull error naming the image,
 * or silently starts yesterday's code. `--rebuild` produces them first.
 */
const JAVA_SERVICES = ['tenant', 'meet', 'notification'] as const;

/**
 * Builds one service's container image with the Gradle wrapper.
 *
 * Run from the repository root with an absolute wrapper path so the command
 * behaves the same from any directory. Output is streamed: a `bootBuildImage`
 * run takes minutes and reports each layer as it goes, and a captured build
 * looks identical to a stalled one until it finishes.
 */
async function buildServiceImage(service: string): Promise<number> {
    const result = await $({
        nothrow: true,
        cwd: repositoryRoot,
        stdio: 'inherit',
        verbose: true,
    })`./services/gradlew -p ${`services/${service}`} bootBuildImage`;

    return result.exitCode ?? 1;
}

export const upCommand = defineCommand({
    meta: {
        name: 'up',
        description: 'Start the development stack in the background',
    },
    args: {
        profile: {
            type: 'string',
            description:
                'Compose profile to enable; observability is the only one defined',
        },
        rebuild: {
            type: 'boolean',
            description:
                'Rebuild the three Java images and the gateway before starting',
            default: false,
        },
    },
    async run({ args }) {
        if (args.rebuild) {
            for (const service of JAVA_SERVICES) {
                const exitCode = await buildServiceImage(service);

                if (exitCode !== 0) {
                    console.error(
                        `\nbootBuildImage failed for ${service} (exit ${exitCode}). `
                            + 'The stack was not started: compose would pull the '
                            + 'previous image and start stale code instead.',
                    );
                    process.exitCode = exitCode;
                    return;
                }
            }
        }

        const profileArgs =
            args.profile === undefined ? [] : ['--profile', args.profile];
        const buildArgs = args.rebuild ? ['--build'] : [];

        const exitCode = await runCompose(DEV_STACK, [
            ...profileArgs,
            'up',
            '-d',
            ...buildArgs,
        ]);

        if (exitCode !== 0) {
            process.exitCode = exitCode;
            return;
        }

        console.log('\nGateway: http://localhost:30000');

        if (args.profile === 'observability') {
            console.log('Grafana: http://localhost:3000');
            console.log(
                'Metrics and JSON logs need images built after the '
                    + 'observability change — run with --rebuild if the '
                    + 'spring-services targets report down.',
            );
        }
    },
});
