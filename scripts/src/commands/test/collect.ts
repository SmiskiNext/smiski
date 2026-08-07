import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { defineCommand } from 'citty';
import { LOAD_GENERATOR_CONTAINER_NAME } from '../../lib/loadtest.ts';
import { testResultsDirectory } from '../../lib/paths.ts';

const DEFAULT_PROMETHEUS_ORIGIN = 'http://localhost:9090';

/**
 * A measured series, and the test-plan metric it stands for.
 *
 * `substitutes` is populated only when the test plan names a metric the stack
 * cannot produce. It is carried into the exported file so a report cannot
 * present a substituted figure as the original — the specification requires the
 * substitution to be labelled, not silently accepted.
 */
interface SeriesDefinition {
    name: string;
    query: string;
    unit: string;
    substitutes?: string;
}

interface CaseDefinition {
    id: string;
    description: string;
    series: SeriesDefinition[];
}

/**
 * Per-case series, chosen by what each source can actually observe.
 *
 * Every query below was checked against the running stack's own `/metrics`
 * output rather than inferred from documentation, because a wrong metric name
 * does not error — it returns an empty result set that reads as a zero reading.
 *
 * The media server publishes its quality metrics as HISTOGRAMS
 * (`livekit_jitter_us_bucket` / `_sum` / `_count`), not as gauges. A bare
 * `livekit_jitter_us` selector matches nothing at all, so each is read as a
 * `_sum / _count` ratio, which is the mean over the scrape interval. The ratio
 * is guarded with `> 0` on the denominator: without it an idle window divides
 * zero by zero and exports `NaN`, which is indistinguishable from a genuine
 * reading of zero jitter once it reaches a spreadsheet.
 *
 * Per-container NETWORK bytes are deliberately absent. Verified against this
 * stack: cAdvisor emits `container_network_receive_bytes_total` with only an
 * `id`, `instance` and `interface` label and no `name`, so it cannot be
 * attributed to a container at all. The media server's own
 * `process_network_*_bytes_total` counters are used instead, which are correctly
 * attributed because they come from its own exporter.
 *
 * Container CPU and MEMORY do carry `name`, so those remain per-container.
 * A query with no `name` filter would aggregate the load generator's own
 * consumption into the system under test's figures, so TC-05 keeps the load
 * generator as its own series rather than excluding it silently.
 */
const CASES: CaseDefinition[] = [
    {
        id: 'tc01',
        description: 'NAT traversal via TURN relay',
        series: [
            {
                name: 'coturn_allocations_total',
                query: 'turn_total_allocations',
                unit: 'count',
            },
            {
                name: 'coturn_relayed_bytes_sent',
                query: 'turn_total_traffic_sentb',
                unit: 'bytes',
            },
            {
                name: 'coturn_relayed_bytes_received',
                query: 'turn_total_traffic_rcvb',
                unit: 'bytes',
            },
            {
                name: 'coturn_peer_bytes_sent',
                query: 'turn_total_traffic_peer_sentb',
                unit: 'bytes',
            },
            {
                name: 'livekit_participants',
                query: 'livekit_participant_total',
                unit: 'count',
            },
        ],
    },
    {
        id: 'tc02',
        description: 'Media stream quality of service',
        series: [
            {
                name: 'livekit_packet_loss_percent_mean',
                query:
                    'rate(livekit_packet_loss_percent_sum[1m])'
                    + ' / (rate(livekit_packet_loss_percent_count[1m]) > 0)',
                unit: 'percent',
            },
            {
                name: 'livekit_jitter_microseconds_mean',
                query:
                    'rate(livekit_jitter_us_sum[1m])'
                    + ' / (rate(livekit_jitter_us_count[1m]) > 0)',
                unit: 'microseconds',
            },
            {
                name: 'livekit_rtt_milliseconds_mean',
                query: 'rate(livekit_rtt_ms_sum[1m]) / (rate(livekit_rtt_ms_count[1m]) > 0)',
                unit: 'milliseconds',
            },
            {
                name: 'livekit_quality_score_mean',
                query:
                    'rate(livekit_quality_score_sum[1m])'
                    + ' / (rate(livekit_quality_score_count[1m]) > 0)',
                unit: 'score',
            },
        ],
    },
    {
        id: 'tc03',
        description: 'Room capacity',
        series: [
            {
                name: 'livekit_participants',
                query: 'livekit_participant_total',
                unit: 'count',
            },
            {
                name: 'livekit_tracks_published',
                query: 'livekit_track_published_total',
                unit: 'count',
            },
            {
                name: 'livekit_tracks_subscribed',
                query: 'livekit_track_subscribed_total',
                unit: 'count',
            },
            {
                name: 'livekit_rooms',
                query: 'livekit_room_total',
                unit: 'count',
            },
            {
                name: 'livekit_packet_bytes_per_second',
                query: 'rate(livekit_packet_bytes[1m])',
                unit: 'bytes/s',
            },
            {
                name: 'livekit_network_receive_bytes_per_second',
                query: 'rate(process_network_receive_bytes_total{job="livekit"}[1m])',
                unit: 'bytes/s',
                substitutes:
                    'Per-container network I/O from cAdvisor, which cannot be used: '
                    + 'verified on this stack, container_network_* carries no `name` '
                    + 'label and so cannot be attributed to a container. The media '
                    + "server's own process-level counter is measured instead — it covers "
                    + 'the same process but excludes any other traffic in its namespace.',
            },
            {
                name: 'livekit_network_transmit_bytes_per_second',
                query: 'rate(process_network_transmit_bytes_total{job="livekit"}[1m])',
                unit: 'bytes/s',
                substitutes:
                    'Per-container network I/O from cAdvisor, unattributable for the '
                    + 'same reason as the receive series above.',
            },
        ],
    },
    {
        id: 'tc04',
        description: 'Token generation and state synchronisation',
        series: [
            {
                name: 'envoy_downstream_request_rate',
                query: 'rate(envoy_http_downstream_rq_total[1m])',
                unit: 'requests/s',
            },
            {
                name: 'envoy_downstream_requests_active',
                query: 'envoy_http_downstream_rq_active',
                unit: 'count',
            },
            {
                name: 'valkey_commands_per_second',
                query: 'rate(redis_commands_processed_total{job="valkey"}[1m])',
                unit: 'commands/s',
            },
            {
                name: 'valkey_keys',
                query: 'redis_db_keys{job="valkey"}',
                unit: 'count',
            },
            {
                name: 'meet_request_rate',
                query: 'rate(http_server_requests_seconds_count{job="spring-services"}[1m])',
                unit: 'requests/s',
            },
            {
                name: 'outbox_backlog_rows',
                query: 'pg_stat_user_tables_n_live_tup{relname="outbox_event"}',
                unit: 'rows',
                substitutes:
                    'Kafka consumer lag, which the stack cannot expose: '
                    + 'apache/kafka:4.1.0 bundles no JMX exporter and is deliberately '
                    + 'absent from the scrape targets. Outbox backlog is measured in its '
                    + 'place and is NOT the same quantity — it counts undrained rows, not '
                    + 'the broker offset difference.',
            },
        ],
    },
    {
        id: 'tc05',
        description: 'Container resource usage under load',
        series: [
            {
                name: 'livekit_cpu_seconds_per_second',
                query: 'rate(container_cpu_usage_seconds_total{name=~".*livekit-server.*"}[1m])',
                unit: 'cores',
            },
            {
                name: 'livekit_memory_usage_bytes',
                query: 'container_memory_usage_bytes{name=~".*livekit-server.*"}',
                unit: 'bytes',
            },
            {
                name: 'valkey_memory_used_bytes',
                query: 'redis_memory_used_bytes{job="valkey"}',
                unit: 'bytes',
            },
            {
                name: 'livekit_redis_memory_used_bytes',
                query: 'redis_memory_used_bytes{job="livekit-redis"}',
                unit: 'bytes',
            },
            {
                name: 'coturn_cpu_seconds_per_second',
                query: 'rate(container_cpu_usage_seconds_total{name=~".*coturn.*"}[1m])',
                unit: 'cores',
            },
            {
                name: 'coturn_memory_usage_bytes',
                query: 'container_memory_usage_bytes{name=~".*coturn.*"}',
                unit: 'bytes',
            },
            {
                name: 'spring_service_cpu_seconds_per_second',
                query:
                    'rate(container_cpu_usage_seconds_total'
                    + '{name=~".*(tenant|meet|notification)-1"}[1m])',
                unit: 'cores',
            },
            {
                name: 'postgres_cpu_seconds_per_second',
                query: 'rate(container_cpu_usage_seconds_total{name=~".*postgres-1"}[1m])',
                unit: 'cores',
            },
            {
                name: 'load_generator_cpu_seconds_per_second',
                query:
                    'rate(container_cpu_usage_seconds_total'
                    + `{name="${LOAD_GENERATOR_CONTAINER_NAME}"}[1m])`,
                unit: 'cores',
                substitutes:
                    'Not a substitution but a caveat: the load generator runs as a '
                    + 'transient container, so this series exists only while a run is in '
                    + 'flight and a window collected after the run ends is legitimately '
                    + 'empty. It is collected so its consumption can be subtracted from '
                    + 'the host figures rather than silently attributed to the system '
                    + 'under test.',
            },
        ],
    },
];

interface RangeSample {
    timestamp: number;
    labels: Record<string, string>;
    value: string;
}

async function queryRange(
    origin: string,
    query: string,
    start: number,
    end: number,
    stepSeconds: number,
): Promise<{ samples: RangeSample[]; error: string | null }> {
    const url = new URL('/api/v1/query_range', origin);
    url.searchParams.set('query', query);
    url.searchParams.set('start', String(start));
    url.searchParams.set('end', String(end));
    url.searchParams.set('step', String(stepSeconds));

    const response = await fetch(url).catch((error: unknown) => error as Error);

    if (response instanceof Error) {
        return { samples: [], error: response.message };
    }

    if (!response.ok) {
        return {
            samples: [],
            error: `HTTP ${response.status} from the metrics API`,
        };
    }

    const body = (await response.json()) as {
        status?: string;
        error?: string;
        data?: {
            result?: Array<{
                metric?: Record<string, string>;
                values?: Array<[number, string]>;
            }>;
        };
    };

    if (body.status !== 'success') {
        return { samples: [], error: body.error ?? 'query rejected' };
    }

    const samples: RangeSample[] = [];
    for (const entry of body.data?.result ?? []) {
        for (const [timestamp, value] of entry.values ?? []) {
            samples.push({
                timestamp,
                labels: entry.metric ?? {},
                value,
            });
        }
    }

    return { samples, error: null };
}

function escapeCsvField(value: string): string {
    return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

/**
 * Writes one file per case, with the run conditions as leading comment rows.
 *
 * The conditions are inside the file rather than only in its name because a
 * figure read without its network profile, cache state, admission policy and
 * variant is not interpretable — a 1 ms latency measured on an unimpaired
 * baseline says nothing about a 200 ms budget under a mobile network.
 */
function buildCsv(
    caseDefinition: CaseDefinition,
    conditions: Record<string, string>,
    rows: Array<{
        series: SeriesDefinition;
        samples: RangeSample[];
        error: string | null;
    }>,
): string {
    const lines: string[] = [];

    lines.push(`# test case,${caseDefinition.id}`);
    lines.push(`# description,${caseDefinition.description}`);
    for (const [key, value] of Object.entries(conditions)) {
        lines.push(`# ${key},${escapeCsvField(value)}`);
    }

    for (const row of rows) {
        if (row.series.substitutes !== undefined) {
            lines.push(
                `# SUBSTITUTED,${escapeCsvField(row.series.name)},${escapeCsvField(row.series.substitutes)}`,
            );
        }
        if (row.error !== null) {
            lines.push(
                `# UNAVAILABLE,${escapeCsvField(row.series.name)},${escapeCsvField(row.error)}`,
            );
        } else if (row.samples.length === 0) {
            lines.push(
                `# EMPTY,${escapeCsvField(row.series.name)},`
                    + 'query succeeded but returned no samples — the target is likely down',
            );
        }
    }

    lines.push('series,unit,timestamp,iso8601,container,job,value');

    for (const row of rows) {
        for (const sample of row.samples) {
            lines.push(
                [
                    row.series.name,
                    row.series.unit,
                    String(sample.timestamp),
                    new Date(sample.timestamp * 1000).toISOString(),
                    sample.labels.name ?? '',
                    sample.labels.job ?? '',
                    sample.value,
                ]
                    .map(escapeCsvField)
                    .join(','),
            );
        }
    }

    return `${lines.join('\n')}\n`;
}

export const collectCommand = defineCommand({
    meta: {
        name: 'collect',
        description:
            'Query the metrics range API and write one file per test case',
    },
    args: {
        'prometheus-origin': {
            type: 'string',
            description: 'Origin of the metrics API',
            default: DEFAULT_PROMETHEUS_ORIGIN,
        },
        since: {
            type: 'string',
            description: 'Window length in minutes ending now',
            default: '30',
        },
        step: {
            type: 'string',
            description: 'Sample interval in seconds',
            default: '15',
        },
        cases: {
            type: 'string',
            description: 'Comma-separated case ids, or all for every case',
            default: 'all',
        },
        'network-profile': {
            type: 'string',
            description: 'Impairment profile in force during the run',
            default: 'lan',
        },
        'cache-state': {
            type: 'string',
            description: 'cold or warm',
            default: 'warm',
        },
        'admission-policy': {
            type: 'string',
            description: 'ALLOW_ALL or MANUAL_APPROVAL',
            default: 'ALLOW_ALL',
        },
        variant: {
            type: 'string',
            description: 'single or sharded',
            default: 'sharded',
        },
        'out-dir': {
            type: 'string',
            description: 'Directory the case files are written to',
            default: testResultsDirectory,
        },
    },
    async run({ args }) {
        const sinceMinutes = Number.parseInt(args.since, 10);
        const stepSeconds = Number.parseInt(args.step, 10);

        if (
            !Number.isInteger(sinceMinutes)
            || sinceMinutes <= 0
            || !Number.isInteger(stepSeconds)
            || stepSeconds <= 0
        ) {
            console.error('--since and --step must both be positive integers');
            process.exitCode = 1;
            return;
        }

        const selected =
            args.cases === 'all'
                ? CASES
                : CASES.filter((entry) =>
                      args.cases
                          .split(',')
                          .map((part) => part.trim().toLowerCase())
                          .includes(entry.id),
                  );

        if (selected.length === 0) {
            console.error(
                `No case matched "${args.cases}". Available: `
                    + `${CASES.map((entry) => entry.id).join(', ')}`,
            );
            process.exitCode = 1;
            return;
        }

        const end = Math.floor(Date.now() / 1000);
        const start = end - sinceMinutes * 60;

        const conditions = {
            'network profile': args['network-profile'],
            'cache state': args['cache-state'],
            'admission policy': args['admission-policy'],
            'variant': args.variant,
            'window start': new Date(start * 1000).toISOString(),
            'window end': new Date(end * 1000).toISOString(),
            'step seconds': String(stepSeconds),
            'jira boundary':
                'mocked — reported latency excludes real Jira round-trip time',
        };

        await mkdir(args['out-dir'], { recursive: true });

        let emptySeriesTotal = 0;

        for (const caseDefinition of selected) {
            const rows = [];

            for (const series of caseDefinition.series) {
                const { samples, error } = await queryRange(
                    args['prometheus-origin'],
                    series.query,
                    start,
                    end,
                    stepSeconds,
                );

                if (error !== null || samples.length === 0) {
                    emptySeriesTotal += 1;
                }

                rows.push({ series, samples, error });
            }

            const fileName =
                `${caseDefinition.id}-${args['network-profile']}`
                + `-${args['cache-state']}-${args.variant}.csv`;
            const path = resolve(args['out-dir'], fileName);

            await writeFile(
                path,
                buildCsv(caseDefinition, conditions, rows),
                'utf8',
            );

            const sampleTotal = rows.reduce(
                (total, row) => total + row.samples.length,
                0,
            );

            console.log(
                `${caseDefinition.id.padEnd(6)} ${String(sampleTotal).padStart(6)} samples`
                    + `  ${rows.length} series  ->  ${path}`,
            );
        }

        if (emptySeriesTotal > 0) {
            console.log(
                `\n${emptySeriesTotal} series returned nothing. Each is labelled `
                    + 'UNAVAILABLE or EMPTY inside its file rather than omitted, so a '
                    + 'missing series cannot be mistaken for a zero reading.',
            );
            console.log(
                'Most likely causes: the observability profile is not running, the '
                    + 'three Java images predate the metrics change, or a scrape target '
                    + 'is down. Check /targets on the metrics UI — a valid configuration '
                    + 'file does not imply a reachable target.',
            );
        }
    },
});
