import { defineCommand, runMain } from 'citty';
import { devCommand } from './commands/dev/index.ts';
import { testCommand } from './commands/test/index.ts';

const main = defineCommand({
    meta: {
        name: 'smiski',
        description: 'Developer CLI for the Smiski monorepo',
    },
    subCommands: {
        dev: devCommand,
        test: testCommand,
    },
});

runMain(main);
