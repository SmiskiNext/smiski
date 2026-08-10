import { defineCommand } from 'citty';
import { cleanCommand } from './clean.ts';
import { configCommand } from './config.ts';
import { downCommand } from './down.ts';
import { upCommand } from './up.ts';

export const devCommand = defineCommand({
    meta: {
        name: 'dev',
        description: 'Local development stack for services/docker',
    },
    subCommands: {
        config: configCommand,
        up: upCommand,
        down: downCommand,
        clean: cleanCommand,
    },
});
