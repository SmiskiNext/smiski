import { randomBytes } from 'node:crypto';
import { existsSync } from 'node:fs';
import { copyFile, readFile, writeFile } from 'node:fs/promises';
import { relative } from 'node:path';
import { defineCommand } from 'citty';
import { consola } from 'consola';
import { $ } from 'zx';
import {
    dockerEnvExample,
    dockerEnvFile,
    repositoryRoot,
} from '../../lib/paths.ts';
import { isInteractive } from '../../lib/prompt.ts';

/**
 * Byte count each generated secret is derived from.
 *
 * Both secrets this fills carry a minimum length the services enforce, and
 * base64url of 32 bytes is 43 characters, which clears it with room to spare.
 */
const SECRET_BYTE_LENGTH = 32;

/** Prefix marking a value in the template as deliberately unusable. */
const PLACEHOLDER_PREFIX = 'change-me';

/** Matches an assignment line, capturing the key and everything after `=`. */
const ASSIGNMENT_PATTERN = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/;

const EXIT_ACTION = '__exit';
const AUTOFILL_ACTION = '__autofill';

/** Menu entries carry a hint so the current value is visible while choosing. */
interface MenuOption {
    label: string;
    value: string;
    hint: string;
}

interface Assignment {
    key: string;
    value: string;
    lineIndex: number;
}

/**
 * A key this command can fill without asking, and where the value comes from.
 *
 * Only keys whose correct value is derivable are listed. The Resend credentials
 * are deliberately absent: a generated string is not a credential the provider
 * accepts, so inventing one would replace an obviously fake placeholder with a
 * plausible-looking value that fails at send time instead.
 */
interface FillableKey {
    key: string;
    description: string;
    source: () => string | null | Promise<string | null>;
    isUnset: (value: string) => boolean;
}

const FILLABLE: FillableKey[] = [
    {
        key: 'CURSOR_SECRET',
        description: 'HMAC key for opaque pagination cursors',
        source: generateSecret,
        isUnset: isPlaceholder,
    },
    {
        key: 'LIVEKIT_API_SECRET',
        description: 'Shared secret LiveKit signs access tokens with',
        source: generateSecret,
        isUnset: isPlaceholder,
    },
    {
        key: 'SMISKI_HOST_IP',
        description: 'Host LAN address LiveKit advertises in ICE candidates',
        source: detectHostAddress,
        isUnset: isPlaceholder,
    },
    {
        key: 'CLOUDFLARE_TUNNEL_TOKEN',
        description:
            'Token associating the cloudflared connector with a named tunnel',
        source: async () => null,
        isUnset: isPlaceholder,
    },
];

/** Returns a URL-safe secret long enough for every consumer's minimum. */
function generateSecret(): string {
    return randomBytes(SECRET_BYTE_LENGTH).toString('base64url');
}

/**
 * Reads the source address the kernel would use to reach the internet.
 *
 * `ip route get 1` resolves a route to 1.0.0.0 and reports the address the host
 * would send from, which is the one a browser on the same network can reach.
 * Enumerating interfaces instead would offer loopback and the docker bridge as
 * equally plausible answers, and LiveKit advertising either produces ICE
 * candidates no participant can connect to.
 *
 * Returns null on any platform where the command is absent or its output does
 * not carry an address, leaving the value for the operator to enter.
 */
async function detectHostAddress(): Promise<string | null> {
    const result = await $({ nothrow: true })`ip route get 1`;

    if (result.exitCode !== 0) {
        return null;
    }

    const [, address] = / src (\S+)/.exec(result.stdout) ?? [];
    return address ?? null;
}

/**
 * Reports whether a value is still the template's placeholder.
 *
 * `SMISKI_HOST_IP` carries no `change-me` marker — the template ships a
 * syntactically valid example address instead, because compose aborts when the
 * variable is unset — so the example itself is treated as unfilled.
 */
function isPlaceholder(value: string): boolean {
    const bare = stripInlineComment(value);
    return (
        bare === ''
        || bare.startsWith(PLACEHOLDER_PREFIX)
        || bare === '192.168.1.10'
    );
}

/**
 * Drops a trailing `# ...` comment from a value.
 *
 * Compose ends a value at unquoted whitespace followed by `#`, so the marker
 * two entries in the template carry is not part of the value the stack sees and
 * must not be part of what is compared against a placeholder.
 */
function stripInlineComment(value: string): string {
    return value.replace(/\s+#.*$/, '').trim();
}

function parseAssignments(lines: string[]): Assignment[] {
    const assignments: Assignment[] = [];

    for (const [lineIndex, line] of lines.entries()) {
        const match = ASSIGNMENT_PATTERN.exec(line);

        if (match === null) {
            continue;
        }

        const [, key = '', value = ''] = match;
        assignments.push({ key, value, lineIndex });
    }

    return assignments;
}

/**
 * Rewrites the assignments named in `changes`, leaving every other line intact.
 *
 * The file is edited line by line rather than parsed and regenerated because
 * roughly two thirds of it is documentation: which entries are required, which
 * are read but not forwarded into a container, and why two consumer groups are
 * commented out. Regenerating would emit a valid file that has lost all of it.
 */
function applyChanges(lines: string[], changes: Map<number, string>): string[] {
    return lines.map((line, index) => changes.get(index) ?? line);
}

/** Hides all but the last four characters of a secret-bearing value. */
function maskValue(key: string, value: string): string {
    const bare = stripInlineComment(value);

    if (bare === '' || !/SECRET|PASSWORD|API_KEY/.test(key)) {
        return bare;
    }

    return `${'*'.repeat(Math.max(bare.length - 4, 0))}${bare.slice(-4)}`;
}

/**
 * Ensures the environment file exists, copying the template when it does not.
 *
 * Returns false only when the template is missing too, which is a broken
 * checkout rather than a state this command can recover from.
 */
async function ensureEnvironmentFile(): Promise<boolean> {
    if (existsSync(dockerEnvFile)) {
        return true;
    }

    if (!existsSync(dockerEnvExample)) {
        consola.error(
            `Neither ${relative(repositoryRoot, dockerEnvFile)} nor `
                + `${relative(repositoryRoot, dockerEnvExample)} exists.`,
        );
        return false;
    }

    await copyFile(dockerEnvExample, dockerEnvFile);
    consola.success(
        `Created ${relative(repositoryRoot, dockerEnvFile)} from `
            + `${relative(repositoryRoot, dockerEnvExample)}.`,
    );

    return true;
}

/**
 * Fills every derivable key that is still carrying a placeholder.
 *
 * Keys already holding a real value are skipped rather than regenerated: both
 * secrets are shared with data already at rest — cursors handed to clients and
 * LiveKit tokens already issued — so replacing one invalidates it.
 */
async function autofill(
    assignments: Assignment[],
): Promise<Map<number, string>> {
    const changes = new Map<number, string>();

    for (const fillable of FILLABLE) {
        const assignment = assignments.find(
            (entry) => entry.key === fillable.key,
        );

        if (assignment === undefined) {
            consola.warn(`${fillable.key} is absent from the file; skipped.`);
            continue;
        }

        if (!fillable.isUnset(assignment.value)) {
            consola.info(`${fillable.key} already set; left unchanged.`);
            continue;
        }

        const generated = await fillable.source();

        if (generated === null) {
            consola.warn(
                `${fillable.key} (${fillable.description}) could not be `
                    + 'determined automatically. Set it manually from this menu.',
            );
            continue;
        }

        changes.set(assignment.lineIndex, `${fillable.key}=${generated}`);
        consola.success(
            `${fillable.key} = ${maskValue(fillable.key, generated)}`,
        );
    }

    return changes;
}

function buildMenuOptions(assignments: Assignment[]): MenuOption[] {
    const pending = assignments.filter((assignment) =>
        FILLABLE.some(
            (fillable) =>
                fillable.key === assignment.key
                && fillable.isUnset(assignment.value),
        ),
    );

    const autofillHint =
        pending.length === 0
            ? 'nothing pending'
            : `${pending.length} pending: ${pending
                  .map((assignment) => assignment.key)
                  .join(', ')}`;

    return [
        {
            label: 'Autogenerate all missing secrets',
            value: AUTOFILL_ACTION,
            hint: autofillHint,
        },
        ...assignments.map((assignment) => ({
            label: assignment.key,
            value: assignment.key,
            hint: maskValue(assignment.key, assignment.value),
        })),
        { label: 'Save and exit', value: EXIT_ACTION, hint: '' },
    ];
}

/**
 * Prompts for a replacement value, returning null when nothing should change.
 *
 * Empty input keeps the current value rather than clearing it: a stray return
 * on a required key would otherwise leave a stack that fails to start, and
 * emptying a key deliberately is rare enough to belong in an editor.
 */
async function promptForValue(assignment: Assignment): Promise<string | null> {
    const current = stripInlineComment(assignment.value);

    const entered = await consola.prompt(`${assignment.key} =`, {
        type: 'text',
        placeholder: current,
        cancel: 'null',
    });

    if (typeof entered !== 'string' || entered.trim() === '') {
        return null;
    }

    return entered.trim();
}

export const configCommand = defineCommand({
    meta: {
        name: 'config',
        description: "Create and edit the development stack's environment file",
    },
    args: {
        check: {
            type: 'boolean',
            description:
                'Validate that all required variables are set, then exit',
        },
    },
    async run({ args }) {
        if (!(await ensureEnvironmentFile())) {
            process.exitCode = 1;
            return;
        }

        const content = await readFile(dockerEnvFile, 'utf8');
        const lines = content.split('\n');
        const assignments = parseAssignments(lines);

        // --check: validate required vars are set, exit 0 (all set) or 1 (missing)
        if (args.check) {
            const missing = FILLABLE.filter((fillable) => {
                const assignment = assignments.find(
                    (a) => a.key === fillable.key,
                );
                return (
                    assignment === undefined
                    || fillable.isUnset(assignment.value)
                );
            });

            if (missing.length === 0) {
                consola.success('All required variables are set.');
                return;
            }

            consola.error(
                `Missing required variables: ${missing.map((f) => f.key).join(', ')}`,
            );
            consola.info(`Run 'smiski dev config' to set them interactively.`);
            process.exitCode = 1;
            return;
        }

        if (!isInteractive()) {
            consola.error(
                'This command edits the file through a menu and needs a '
                    + 'terminal. Run it directly rather than through a pipe.',
            );
            process.exitCode = 1;
            return;
        }

        let liveLines = lines;
        let dirty = false;

        consola.info(
            `Editing ${relative(repositoryRoot, dockerEnvFile)}. Comments and `
                + 'layout are preserved; only the chosen lines are rewritten.',
        );

        while (true) {
            const liveAssignments = parseAssignments(liveLines);

            const selection = await consola.prompt('Select an entry', {
                type: 'select',
                options: buildMenuOptions(liveAssignments),
                cancel: 'null',
            });

            if (selection === null || selection === EXIT_ACTION) {
                break;
            }

            if (selection === AUTOFILL_ACTION) {
                const changes = await autofill(liveAssignments);

                if (changes.size > 0) {
                    liveLines = applyChanges(liveLines, changes);
                    dirty = true;
                }

                consola.info(
                    'RESEND_API_KEY and RESEND_WEBHOOK_SECRET keep their '
                        + 'placeholders: only real credentials from the provider '
                        + 'work, and a generated one fails at send time instead.',
                );
                continue;
            }

            const assignment = liveAssignments.find(
                (entry) => entry.key === selection,
            );

            if (assignment === undefined) {
                continue;
            }

            const entered = await promptForValue(assignment);

            if (entered === null) {
                consola.info(`${assignment.key} left unchanged.`);
                continue;
            }

            liveLines = applyChanges(
                liveLines,
                new Map([[assignment.lineIndex, `${selection}=${entered}`]]),
            );
            dirty = true;

            consola.success(`${selection} = ${maskValue(selection, entered)}`);
        }

        if (!dirty) {
            consola.info('No changes written.');
            return;
        }

        await writeFile(dockerEnvFile, liveLines.join('\n'), 'utf8');
        consola.success(`Wrote ${relative(repositoryRoot, dockerEnvFile)}.`);
        consola.info(
            'Running containers do not see the new values until they are '
                + 'recreated: smiski dev up recreates what changed.',
        );
    },
});
