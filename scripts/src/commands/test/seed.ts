import { defineCommand } from 'citty';
import { runPsql } from '../../lib/docker.ts';
import {
    HARNESS_HOST_ACCOUNT_ID,
    HARNESS_ISSUE_ID,
    HARNESS_TENANT_ID,
} from '../../lib/fixtures.ts';

const MEET_POSTGRES_SERVICE = 'meet-postgres';
const DEFAULT_DATABASE = 'meet';
const DEFAULT_USER = 'smiski_user';

const MIN_MAX_PARTICIPANTS = 2;
const MAX_MAX_PARTICIPANTS = 100;

type AdmissionPolicy = 'ALLOW_ALL' | 'MANUAL_APPROVAL';

interface SeededMeeting {
    id: string;
    admissionPolicy: AdmissionPolicy;
}

/**
 * SQL that makes the tenant row exist before any meeting references it.
 *
 * `meetings.tenant_id` carries a foreign key to `tenants(tenant_id)`, so seeding
 * meetings first fails on the constraint. The projection is normally populated
 * from the tenant service over Kafka, which never happens for a synthetic
 * tenant, so the harness writes it directly.
 *
 * `ON CONFLICT DO NOTHING` is what makes re-running safe.
 */
const UPSERT_TENANT_SQL = `
INSERT INTO tenants (tenant_id, cloud_id, status, updated_at)
VALUES ($TENANT$, $TENANT$, 'ACTIVE', NOW())
ON CONFLICT (tenant_id) DO NOTHING;
`;

/**
 * Builds the statement inserting one joinable meeting.
 *
 * Three column values decide whether the meeting can be joined at all, and none
 * of them raises an error when wrong:
 *
 * - `status = 'RUNNING'` — any other status is not returned by the active lookup
 * - `deleted_at IS NULL` — the repository filters on it, and every relevant index
 *   is partial on the same predicate
 * - `settings.admissionPolicy` — decides whether a joiner is admitted or queued,
 *   which is what separates TC-04a from TC-04b
 *
 * `short_code` is unique per tenant on a partial index, so it is derived from the
 * generated identifier rather than fixed.
 */
function buildMeetingInsert(
    tenantId: string,
    admissionPolicy: AdmissionPolicy,
    maxParticipants: number,
    issueId: string,
): string {
    const settings = JSON.stringify({
        admissionPolicy,
        maxParticipants,
        allowScreenShare: true,
        chatEnabled: true,
        allowMicrophone: true,
        allowVideo: true,
    });

    return `
WITH generated AS (
    SELECT uuidv7() AS id
)
INSERT INTO meetings (
    tenant_id, id, host_id, organizer_email, organizer_display_name,
    calendar_uid, calendar_sequence, short_code, issue_id, issue_key,
    project_key, title, description, zone_id, type, status, settings
)
SELECT
    $TENANT$, generated.id, '${HARNESS_HOST_ACCOUNT_ID}',
    'loadtest-host@smiski.test', 'Load Test Host',
    'loadtest-' || generated.id, 0,
    substring(replace(generated.id::text, '-', '') for 12),
    '${issueId}', 'LOAD-1', 'LOAD',
    'Load test ${admissionPolicy}', 'Seeded by smiski test seed',
    'UTC', 'INSTANT', 'RUNNING', '${settings}'::jsonb
FROM generated
RETURNING id;
`.replace(/\$TENANT\$/g, `'${tenantId}'`);
}

/**
 * Removes only the fixtures this command created, leaving other data intact.
 *
 * Scoped by `project_key` rather than by tenant so a re-run cannot delete rows
 * another session seeded under the same tenant. `participation_logs` and
 * `meeting_invitees` cascade from `meetings`, so they need no separate statement.
 */
const DELETE_PRIOR_FIXTURES_SQL = `
DELETE FROM meetings WHERE tenant_id = $TENANT$ AND project_key = 'LOAD';
`;

export const seedCommand = defineCommand({
    meta: {
        name: 'seed',
        description:
            'Insert the tenant and meetings measurement needs, safe to re-run',
    },
    args: {
        'tenant-id': {
            type: 'string',
            description:
                'Tenant to seed; must equal the token cloudId or joins yield 404',
            default: HARNESS_TENANT_ID,
        },
        'allow-all-count': {
            type: 'string',
            description:
                'Number of ALLOW_ALL meetings, for the sharded token throughput variant',
            default: '10',
        },
        'manual-approval-count': {
            type: 'string',
            description:
                'Number of MANUAL_APPROVAL meetings, for the cached approval state run',
            default: '2',
        },
        'max-participants': {
            type: 'string',
            description: 'Participant limit per meeting (2-100)',
            default: String(MAX_MAX_PARTICIPANTS),
        },
        'issue-id': {
            type: 'string',
            description: 'Numeric Jira issue id stored on each meeting',
            default: HARNESS_ISSUE_ID,
        },
        keep: {
            type: 'boolean',
            description: 'Add to existing fixtures instead of replacing them',
            default: false,
        },
        database: {
            type: 'string',
            description: 'Postgres database name',
            default: DEFAULT_DATABASE,
        },
        user: {
            type: 'string',
            description: 'Postgres user',
            default: DEFAULT_USER,
        },
    },
    async run({ args }) {
        const allowAllCount = Number.parseInt(args['allow-all-count'], 10);
        const manualApprovalCount = Number.parseInt(
            args['manual-approval-count'],
            10,
        );
        const maxParticipants = Number.parseInt(args['max-participants'], 10);

        const validationError = validateCounts(
            allowAllCount,
            manualApprovalCount,
            maxParticipants,
        );
        if (validationError !== null) {
            console.error(validationError);
            process.exitCode = 1;
            return;
        }

        const tenantId = args['tenant-id'];

        const tenantResult = await runPsql(
            MEET_POSTGRES_SERVICE,
            args.database,
            args.user,
            UPSERT_TENANT_SQL.replace(/\$TENANT\$/g, `'${tenantId}'`),
        );

        if (tenantResult.exitCode !== 0) {
            console.error('Failed to upsert the tenant row.');
            console.error(tenantResult.stderr.trim());
            console.error(
                '\nIs the test stack running? Seeding writes through '
                    + `${MEET_POSTGRES_SERVICE} in the smiski-test project.`,
            );
            process.exitCode = 1;
            return;
        }

        if (!args.keep) {
            const cleanup = await runPsql(
                MEET_POSTGRES_SERVICE,
                args.database,
                args.user,
                DELETE_PRIOR_FIXTURES_SQL.replace(
                    /\$TENANT\$/g,
                    `'${tenantId}'`,
                ),
            );

            if (cleanup.exitCode !== 0) {
                console.error('Failed to remove prior fixtures.');
                console.error(cleanup.stderr.trim());
                process.exitCode = 1;
                return;
            }
        }

        const seeded: SeededMeeting[] = [];

        for (const [policy, count] of [
            ['ALLOW_ALL', allowAllCount],
            ['MANUAL_APPROVAL', manualApprovalCount],
        ] as Array<[AdmissionPolicy, number]>) {
            for (let index = 0; index < count; index += 1) {
                const inserted = await runPsql(
                    MEET_POSTGRES_SERVICE,
                    args.database,
                    args.user,
                    buildMeetingInsert(
                        tenantId,
                        policy,
                        maxParticipants,
                        args['issue-id'],
                    ),
                );

                if (inserted.exitCode !== 0) {
                    console.error(`Failed to insert a ${policy} meeting.`);
                    console.error(inserted.stderr.trim());
                    process.exitCode = 1;
                    return;
                }

                const id = inserted.stdout.trim().split('\n')[0]?.trim();
                if (id === undefined || id === '') {
                    console.error(
                        `Insert of a ${policy} meeting returned no identifier.`,
                    );
                    process.exitCode = 1;
                    return;
                }

                seeded.push({ id, admissionPolicy: policy });
            }
        }

        reportSeeded(tenantId, args['issue-id'], maxParticipants, seeded);
    },
});

function validateCounts(
    allowAllCount: number,
    manualApprovalCount: number,
    maxParticipants: number,
): string | null {
    if (!Number.isInteger(allowAllCount) || allowAllCount < 0) {
        return '--allow-all-count must be a non-negative integer';
    }

    if (!Number.isInteger(manualApprovalCount) || manualApprovalCount < 0) {
        return '--manual-approval-count must be a non-negative integer';
    }

    if (
        !Number.isInteger(maxParticipants)
        || maxParticipants < MIN_MAX_PARTICIPANTS
        || maxParticipants > MAX_MAX_PARTICIPANTS
    ) {
        return (
            `--max-participants must be between ${MIN_MAX_PARTICIPANTS} and `
            + `${MAX_MAX_PARTICIPANTS}: the domain constructor rejects anything else, `
            + 'so an out-of-range row loads as a 500 rather than as bad data'
        );
    }

    return null;
}

function reportSeeded(
    tenantId: string,
    issueId: string,
    maxParticipants: number,
    seeded: SeededMeeting[],
): void {
    const allowAll = seeded.filter(
        (meeting) => meeting.admissionPolicy === 'ALLOW_ALL',
    );
    const manualApproval = seeded.filter(
        (meeting) => meeting.admissionPolicy === 'MANUAL_APPROVAL',
    );

    console.log(`tenant            ${tenantId}`);
    console.log(`host account      ${HARNESS_HOST_ACCOUNT_ID}`);
    console.log(`issue id          ${issueId}`);
    console.log(`max participants  ${maxParticipants}`);
    console.log(`\nALLOW_ALL meetings (${allowAll.length})`);
    for (const meeting of allowAll) {
        console.log(`  ${meeting.id}`);
    }
    console.log(`\nMANUAL_APPROVAL meetings (${manualApproval.length})`);
    for (const meeting of manualApproval) {
        console.log(`  ${meeting.id}`);
    }

    console.log(
        '\nThe token cloudId must equal the tenant above. A mismatch is not an '
            + 'auth failure: it passes authentication and then yields 404 from the '
            + 'tenant filter, which reads as a missing meeting.',
    );
}
