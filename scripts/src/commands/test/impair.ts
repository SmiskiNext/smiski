import { defineCommand } from 'citty';
import {
    resolveContainerName,
    runInNetworkNamespace,
} from '../../lib/docker.ts';

const IMPAIRED_INTERFACE = 'eth0';

/** LiveKit's ICE/UDP mux port, and the one blocked-UDP must drop. */
const MEDIA_UDP_PORT = '7882';

/**
 * TURN's UDP port, blocked alongside the media path.
 *
 * TC-01 blocks EVERY UDP port, so TURN/UDP must go too — which is exactly why
 * Coturn is configured for TURN over TCP. Leaving UDP/3478 reachable would let
 * a relay allocation succeed over a transport the test means to exclude, and the
 * run would record a pass that proves nothing.
 */
const TURN_UDP_PORT = '3478';

type ProfileName = 'lan' | '4g' | 'blocked-udp';

interface Profile {
    description: string;
    apply: string[][];
    remove: string[][];
}

/**
 * The three named network conditions, and what each one actually configures.
 *
 * `lan` is the absence of impairment rather than a setting, and it exists as a
 * named profile so evidence can state that the baseline was deliberate. A media
 * quality figure gathered here carries no information about behavior under a
 * degraded network: local latency is around 1 ms, so "< 200 ms" is satisfied by
 * doing nothing.
 *
 * `4g` adds latency, jitter and loss in one `netem` qdisc. The jitter argument
 * is the second delay value; without `distribution normal` the variation is
 * uniform, which no real mobile network resembles.
 *
 * `blocked-udp` drops outbound UDP on the media and TURN ports. `iptables` is
 * used rather than `netem` because the requirement is a hard block, not loss:
 * a 100% loss qdisc would still let ICE consider the path viable for a while.
 */
const PROFILES: Record<ProfileName, Profile> = {
    'lan': {
        description: 'Unimpaired baseline; no qdisc and no filter rules',
        apply: [],
        remove: [],
    },
    '4g': {
        description:
            'Mobile network: 60 ms delay, 20 ms jitter, 1% loss, normal distribution',
        apply: [
            [
                'tc',
                'qdisc',
                'add',
                'dev',
                IMPAIRED_INTERFACE,
                'root',
                'netem',
                'delay',
                '60ms',
                '20ms',
                'distribution',
                'normal',
                'loss',
                '1%',
            ],
        ],
        remove: [['tc', 'qdisc', 'del', 'dev', IMPAIRED_INTERFACE, 'root']],
    },
    'blocked-udp': {
        description: `Corporate network: outbound UDP dropped on ${MEDIA_UDP_PORT} and ${TURN_UDP_PORT}`,
        apply: [
            [
                'iptables',
                '-A',
                'OUTPUT',
                '-p',
                'udp',
                '--dport',
                MEDIA_UDP_PORT,
                '-j',
                'DROP',
            ],
            [
                'iptables',
                '-A',
                'OUTPUT',
                '-p',
                'udp',
                '--dport',
                TURN_UDP_PORT,
                '-j',
                'DROP',
            ],
        ],
        remove: [
            [
                'iptables',
                '-D',
                'OUTPUT',
                '-p',
                'udp',
                '--dport',
                MEDIA_UDP_PORT,
                '-j',
                'DROP',
            ],
            [
                'iptables',
                '-D',
                'OUTPUT',
                '-p',
                'udp',
                '--dport',
                TURN_UDP_PORT,
                '-j',
                'DROP',
            ],
        ],
    },
};

const INSPECTION_COMMANDS: string[][] = [
    ['tc', 'qdisc', 'show', 'dev', IMPAIRED_INTERFACE],
    ['iptables', '-S', 'OUTPUT'],
];

/**
 * Applies or removes a named network-impairment profile.
 *
 * Removal treats a non-zero exit as success. `tc` and `iptables` both fail when
 * asked to delete a rule that is not present, and that is the desired end state
 * rather than an error: profiles must be idempotent so an interrupted run can be
 * cleaned up without first inspecting what it managed to apply. Application, by
 * contrast, aborts on the first failing step — a half-applied profile would
 * silently produce measurements under conditions nobody chose.
 */
export const impairCommand = defineCommand({
    meta: {
        name: 'impair',
        description:
            'Apply, remove or inspect a named network-impairment profile',
    },
    args: {
        profile: {
            type: 'positional',
            description: 'lan, 4g or blocked-udp',
            required: false,
            default: 'lan',
        },
        service: {
            type: 'string',
            description:
                'Test-stack service whose network namespace is changed',
            default: 'livekit-server',
        },
        remove: {
            type: 'boolean',
            description: 'Remove the named profile instead of applying it',
            default: false,
        },
        show: {
            type: 'boolean',
            description: 'Print the current qdisc and filter rules and exit',
            default: false,
        },
    },
    async run({ args }) {
        const profileName = args.profile as ProfileName;
        const profile = PROFILES[profileName];

        if (profile === undefined && !args.show) {
            console.error(
                `Unknown profile "${args.profile}". Available: ${Object.keys(PROFILES).join(', ')}`,
            );
            process.exitCode = 1;
            return;
        }

        const containerName = await resolveContainerName(args.service);
        if (containerName === null) {
            console.error(
                `No running container for service "${args.service}" in the smiski-test `
                    + 'project. Start the test stack first.',
            );
            process.exitCode = 1;
            return;
        }

        if (args.show) {
            await reportCurrentState(containerName, args.service);
            return;
        }

        if (profileName === 'lan' && !args.remove) {
            await clearAllImpairment(containerName);
            console.log(
                `${args.service}: baseline restored — every qdisc and filter rule removed.`,
            );
            console.log(
                'Recorded as the `lan` profile. A quality figure measured here is '
                    + 'not evidence of behavior under a degraded network.',
            );
            return;
        }

        const steps = args.remove ? profile.remove : profile.apply;
        const verb = args.remove ? 'removed' : 'applied';

        for (const step of steps) {
            const outcome = await runInNetworkNamespace(containerName, step);

            if (outcome.exitCode !== 0) {
                const message = outcome.stderr.trim() || outcome.stdout.trim();
                const removalIsIdempotent = args.remove;

                if (removalIsIdempotent) {
                    console.log(
                        `  ${step.join(' ')} -> nothing to remove (${message})`,
                    );
                    continue;
                }

                console.error(`Failed: ${step.join(' ')}`);
                console.error(message);
                process.exitCode = 1;
                return;
            }

            console.log(`  ${step.join(' ')}`);
        }

        console.log(
            `\n${args.service}: profile "${profileName}" ${verb} — ${profile.description}`,
        );

        await reportCurrentState(containerName, args.service);

        if (!args.remove) {
            console.log(
                `\nReverse without restarting the stack:\n`
                    + `  smiski test impair ${profileName} --service ${args.service} --remove`,
            );
            console.log(
                'Record this profile name in every result gathered while it is in force.',
            );
        }
    },
});

/**
 * Removes every impairment regardless of which profile applied it.
 *
 * Used for the baseline because a partially applied profile leaves state no
 * single `--remove` reverses, and a leftover qdisc silently contaminates the
 * next run's numbers.
 *
 * Each step is expected to fail once nothing is left to remove — `tc` reports a
 * zero handle and `iptables` reports a missing rule — so failures are swallowed
 * rather than printed. Surfacing them would make a successful reset look like an
 * error, which is the opposite of the reassurance the baseline needs to give.
 */
async function clearAllImpairment(containerName: string): Promise<void> {
    await runInNetworkNamespace(containerName, [
        'tc',
        'qdisc',
        'del',
        'dev',
        IMPAIRED_INTERFACE,
        'root',
    ]);

    for (const port of [MEDIA_UDP_PORT, TURN_UDP_PORT]) {
        let removed = true;
        while (removed) {
            const outcome = await runInNetworkNamespace(containerName, [
                'iptables',
                '-D',
                'OUTPUT',
                '-p',
                'udp',
                '--dport',
                port,
                '-j',
                'DROP',
            ]);
            removed = outcome.exitCode === 0;
        }
    }
}

async function reportCurrentState(
    containerName: string,
    service: string,
): Promise<void> {
    console.log(`\ncurrent state of ${service}:`);

    for (const command of INSPECTION_COMMANDS) {
        const outcome = await runInNetworkNamespace(containerName, command);
        const output = (outcome.stdout || outcome.stderr).trim();

        for (const line of output.split('\n')) {
            console.log(`  ${line}`);
        }
    }
}
