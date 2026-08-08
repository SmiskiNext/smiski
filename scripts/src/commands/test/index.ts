import { defineCommand } from 'citty';
import { collectCommand } from './collect.ts';
import { impairCommand } from './impair.ts';
import { keygenCommand } from './keygen.ts';
import { loadtestCommand } from './loadtest/index.ts';
import { seedCommand } from './seed.ts';
import { tokenCommand } from './token.ts';

export const testCommand = defineCommand({
    meta: {
        name: 'test',
        description: 'Load-test harness for services/test',
    },
    subCommands: {
        keygen: keygenCommand,
        token: tokenCommand,
        seed: seedCommand,
        loadtest: loadtestCommand,
        impair: impairCommand,
        collect: collectCommand,
    },
});
