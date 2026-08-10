import { defineCommand } from 'citty';
import {
    isServiceRunning,
    PINNED_IMAGES,
    runOnStackNetwork,
} from '../../../lib/docker.ts';
import { DEFAULT_LIVEKIT_URL } from '../../../lib/fixtures.ts';
import { resolveLiveKitCredentials } from '../../../lib/livekit.ts';

/**
 * Container name the room load simulator runs under.
 *
 * Named for the same reason as the token load generator: cAdvisor attributes
 * resource series by container name, and an unnamed run cannot be separated
 * from the media server it is driving.
 */
const ROOM_GENERATOR_CONTAINER_NAME = 'smiski-test-lk';

export const roomCommand = defineCommand({
    meta: {
        name: 'room',
        description:
            'TC-03: drive one room to the configured participant counts',
    },
    args: {
        room: {
            type: 'string',
            description: 'Room name to drive',
            default: 'tc03-capacity',
        },
        duration: {
            type: 'string',
            description: 'Run duration, e.g. 2m',
            default: '2m',
        },
        'video-publishers': {
            type: 'string',
            description: 'Participants publishing camera video',
            default: '10',
        },
        'audio-publishers': {
            type: 'string',
            description: 'Participants publishing audio only',
            default: '0',
        },
        subscribers: {
            type: 'string',
            description: 'Participants that only subscribe',
            default: '20',
        },
        'video-resolution': {
            type: 'string',
            description: 'high (720p), medium (360p) or low (180p)',
            default: 'high',
        },
        'num-per-second': {
            type: 'string',
            description: 'Participants started per second',
            default: '5',
        },
        layout: {
            type: 'string',
            description: 'speaker, 3x3, 4x4 or 5x5',
            default: 'speaker',
        },
        'livekit-url': {
            type: 'string',
            description: 'LiveKit signalling URL on the stack network',
            default: DEFAULT_LIVEKIT_URL,
        },
        'api-key': {
            type: 'string',
            description:
                'LiveKit API key; defaults to the value the running stack '
                + 'was started with, read from .env',
        },
        'api-secret': {
            type: 'string',
            description:
                'LiveKit API secret; defaults to the value the running stack '
                + 'was started with, read from .env',
        },
        'no-simulcast': {
            type: 'boolean',
            description: 'Disable simulcast publishing',
            default: false,
        },
        'simulate-speakers': {
            type: 'boolean',
            description: 'Fire random speaker events',
            default: false,
        },
    },
    async run({ args }) {
        if (!(await isServiceRunning('livekit-server'))) {
            console.error(
                'livekit-server is not running. Start the test stack first.',
            );
            process.exitCode = 1;
            return;
        }

        const resolved = resolveLiveKitCredentials();
        const apiKey = args['api-key'] ?? resolved.apiKey;
        const apiSecret = args['api-secret'] ?? resolved.apiSecret;

        const commandArguments = [
            'load-test',
            '--url',
            args['livekit-url'],
            '--api-key',
            apiKey,
            '--api-secret',
            apiSecret,
            '--room',
            args.room,
            '--duration',
            args.duration,
            '--video-publishers',
            args['video-publishers'],
            '--audio-publishers',
            args['audio-publishers'],
            '--subscribers',
            args.subscribers,
            '--video-resolution',
            args['video-resolution'],
            '--num-per-second',
            args['num-per-second'],
            '--layout',
            args.layout,
        ];

        if (args['no-simulcast']) {
            commandArguments.push('--no-simulcast');
        }

        if (args['simulate-speakers']) {
            commandArguments.push('--simulate-speakers');
        }

        reportRoomPreconditions(args.room, apiKey);

        const result = await runOnStackNetwork(
            PINNED_IMAGES.livekitCli,
            commandArguments,
            { containerName: ROOM_GENERATOR_CONTAINER_NAME },
        );

        console.log(result.stdout);

        if (result.stderr.trim() !== '') {
            console.error(result.stderr);
        }

        if (result.exitCode !== 0) {
            console.error(
                `\nlk load-test exited ${result.exitCode}. A non-zero exit is a `
                    + 'counted observation for TC-03, not a harness fault — record it.',
            );
            process.exitCode = result.exitCode;
            return;
        }

        reportRoomCaveats();
    },
});

function reportRoomPreconditions(room: string, apiKey: string): void {
    console.log(
        'lk load-test mints its own tokens from --api-key/--api-secret; a token '
            + 'from `smiski test token` is not accepted and is not needed.',
    );
    console.log(
        `Signing with API key "${apiKey}" — every participant fails with `
            + '"invalid API key" instead of a clearer error if this disagrees '
            + 'with the key livekit-server was started with.',
    );
    console.log(
        'Screen share is NOT driven here: lk load-test hardcodes a camera track '
            + `source. Publish one manually from the harness page into room "${room}" `
            + 'and record the interval from its export.\n',
    );
}

function reportRoomCaveats(): void {
    console.log(
        '\nThe table above is recorded as evidence but is NOT used for threshold '
            + 'assertions: its Latency column is tester-side arrival timing and it '
            + 'reports no jitter at all. Take jitter, packet loss and round-trip time '
            + 'from the media server metrics and the harness page export instead.',
    );
}
