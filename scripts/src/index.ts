import { defineCommand, runMain } from 'citty';
import { testCommand } from './commands/test/index.ts';

const main = defineCommand({
    meta: {
        name: 'smiski',
        description: 'Developer CLI for the Smiski monorepo',
    },
    subCommands: {
        test: testCommand,
    },
});

runMain(main);
