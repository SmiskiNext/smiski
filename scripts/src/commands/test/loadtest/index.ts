import { defineCommand } from 'citty';
import { roomCommand } from './room.ts';
import { tokensCommand } from './tokens.ts';

export const loadtestCommand = defineCommand({
    meta: {
        name: 'loadtest',
        description: 'Drive load through the gateway and the media server',
    },
    subCommands: {
        tokens: tokensCommand,
        room: roomCommand,
    },
});
