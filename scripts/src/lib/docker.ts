import { $ } from 'zx';
import { dockerComposeFile, testComposeFile } from './paths.ts';

$.verbose = false;

/**
 * Pinned images for every tool the harness runs.
 *
 * Nothing is installed on the host: the spec requires measurement to work on a
 * machine carrying only a container runtime. Tags are exact rather than
 * floating so a result can be reproduced later — `latest` would silently change
 * the measured tool between runs.
 */
export const PINNED_IMAGES = {
    k6: 'grafana/k6:2.1.0',
    livekitCli: 'livekit/livekit-cli:v2.18.2',
    netshoot: 'nicolaka/netshoot:v0.13',
} as const;

/**
 * A compose stack the helpers below can address.
 *
 * The project name cannot be derived from the compose file path: compose takes
 * it from a top-level `name:` when one is present and from the containing
 * directory otherwise. Both are recorded here so container names and the stack
 * network can be resolved without re-deriving that rule at each call site.
 */
export interface ComposeStack {
    composeFile: string;
    projectName: string;
}

/** The load-test stack, whose overlay declares `name: smiski-test`. */
export const TEST_STACK: ComposeStack = {
    composeFile: testComposeFile,
    projectName: 'smiski-test',
};

/**
 * The local development stack.
 *
 * Its compose file declares no `name:`, so compose derives the project from the
 * containing directory — `services/docker` yields `docker`. Keeping the two
 * stacks under different project names is what stops one stack's containers,
 * volumes and network from colliding with the other's.
 */
export const DEV_STACK: ComposeStack = {
    composeFile: dockerComposeFile,
    projectName: 'docker',
};

/** Result of a command run without throwing on a non-zero exit. */
export interface CommandOutcome {
    exitCode: number;
    stdout: string;
    stderr: string;
}

function composeBaseArgs(stack: ComposeStack): string[] {
    return ['compose', '-f', stack.composeFile];
}

/**
 * Runs a compose subcommand with its output streamed to the terminal.
 *
 * Streaming rather than capturing because the operations this fronts — image
 * pulls, source builds, container creation — take minutes and report progress
 * as they go. Captured output arrives only at the end, which is
 * indistinguishable from a hang for as long as the command runs.
 */
export async function runCompose(
    stack: ComposeStack,
    args: string[],
): Promise<number> {
    const result = await $({
        nothrow: true,
        stdio: 'inherit',
        verbose: true,
    })`docker ${composeBaseArgs(stack)} ${args}`;

    return result.exitCode ?? 1;
}

/**
 * Resolves the container name of a stack service.
 *
 * Compose derives it from the project name, so it cannot be hardcoded: the
 * test overlay declares `name: smiski-test`, which is what keeps the test
 * stack's containers, volumes and networks distinct from the development
 * stack's.
 */
export async function resolveContainerName(
    service: string,
    stack: ComposeStack = TEST_STACK,
): Promise<string | null> {
    const result = await $({
        nothrow: true,
    })`docker ${composeBaseArgs(stack)} ps --format {{.Name}} ${service}`;

    if (result.exitCode !== 0) {
        return null;
    }

    const name = result.stdout.trim().split('\n')[0]?.trim();
    return name ? name : null;
}

/** Reports whether a stack service has a running container. */
export async function isServiceRunning(
    service: string,
    stack: ComposeStack = TEST_STACK,
): Promise<boolean> {
    const name = await resolveContainerName(service, stack);
    if (name === null) {
        return false;
    }

    const state = await $({
        nothrow: true,
    })`docker inspect -f {{.State.Running}} ${name}`;

    return state.exitCode === 0 && state.stdout.trim() === 'true';
}

/** Runs a command inside a stack container without throwing. */
export async function execInService(
    service: string,
    command: string[],
    stack: ComposeStack = TEST_STACK,
): Promise<CommandOutcome> {
    const result = await $({
        nothrow: true,
    })`docker ${composeBaseArgs(stack)} exec -T ${service} ${command}`;

    return {
        exitCode: result.exitCode ?? 1,
        stdout: result.stdout,
        stderr: result.stderr,
    };
}

/**
 * Runs a command inside a container joined to the test stack's network.
 *
 * Used for tooling that must resolve compose service names — the k6 and
 * `livekit-cli` images are not part of the stack, so without this they would
 * only reach published host ports and could not address `mock-jira`,
 * `livekit-server` or `coturn` at all.
 *
 * `asCurrentUser` exists because the k6 image runs as an unprivileged built-in
 * user, which cannot write into a host-owned bind mount. Without it the run
 * completes and then loses its summary to `permission denied`, so the
 * measurement is spent but unreadable.
 *
 * `containerName` is what makes the run observable. cAdvisor labels its series
 * with the container name, and an unnamed `docker run` receives a random one,
 * so resource queries matching on name silently return no data and the load
 * generator's own consumption cannot be separated from the system under test.
 */
export async function runOnStackNetwork(
    image: string,
    args: string[],
    options: {
        mounts?: string[];
        environment?: Record<string, string>;
        asCurrentUser?: boolean;
        containerName?: string;
    } = {},
): Promise<CommandOutcome> {
    const networkFlag = `--network=${TEST_STACK.projectName}_default`;

    const mountArgs = (options.mounts ?? []).flatMap((mount) => ['-v', mount]);
    const environmentArgs = Object.entries(options.environment ?? {}).flatMap(
        ([key, value]) => ['-e', `${key}=${value}`],
    );
    const userArgs =
        options.asCurrentUser === true
            ? [
                  '--user',
                  `${process.getuid?.() ?? 0}:${process.getgid?.() ?? 0}`,
              ]
            : [];

    const nameArgs =
        options.containerName === undefined
            ? []
            : ['--name', options.containerName];

    const result = await $({
        nothrow: true,
    })`docker run --rm ${networkFlag} ${nameArgs} ${userArgs} ${mountArgs} ${environmentArgs} ${image} ${args}`;

    return {
        exitCode: result.exitCode ?? 1,
        stdout: result.stdout,
        stderr: result.stderr,
    };
}

/**
 * Runs a privileged container sharing a target container's network namespace.
 *
 * `tc` and `iptables` change the namespace, not the container, so impairment can
 * be applied to a container that carries neither binary nor `NET_ADMIN` — which
 * is every container in the inherited development stack. Doing it this way is
 * what keeps `services/docker/` unmodified.
 */
export async function runInNetworkNamespace(
    containerName: string,
    command: string[],
): Promise<CommandOutcome> {
    const result = await $({
        nothrow: true,
        quiet: true,
    })`docker run --rm --network=container:${containerName} --cap-add=NET_ADMIN --cap-add=NET_RAW ${PINNED_IMAGES.netshoot} ${command}`;

    return {
        exitCode: result.exitCode ?? 1,
        stdout: result.stdout,
        stderr: result.stderr,
    };
}

/** Runs `psql` inside a stack Postgres container, returning raw tuples. */
export async function runPsql(
    service: string,
    database: string,
    user: string,
    sql: string,
): Promise<CommandOutcome> {
    return execInService(service, [
        'psql',
        '-U',
        user,
        '-d',
        database,
        '-v',
        'ON_ERROR_STOP=1',
        '-t',
        '-A',
        '-F',
        '\t',
        '-c',
        sql,
    ]);
}
