/**
 * Module-scoped store for the Jira identifiers the gateway needs to scope its
 * permission check.
 *
 * The gateway's authorization service parses `x-issue-id` / `x-project-id`
 * with `strconv.ParseInt` and denies the request when either is non-numeric,
 * so only numeric Jira identifiers may be published here — never a Jira key
 * such as `SMISKI-101`, and never a synthetic value derived from a key.
 *
 * Every surface that calls the backend publishes to this store as it mounts:
 * the issue panel, the project page, and each platform-modal root. Modal roots
 * render in their own iframe whose `context.extension` carries the modal
 * payload rather than the originating issue, which is why the identifiers
 * travel in that payload and are republished here on the other side.
 *
 * Publishing must complete before a surface renders. Surface roots issue backend
 * requests as they mount, and a request that leaves without an identifier makes
 * the gateway skip its permission check and return an empty permission set, so
 * every guarded endpoint answers 403. Surfaces holding only a project key await
 * {@link publishProjectContext} for that reason.
 *
 * `api/forgeRemoteFetch.ts` reads the store when composing request headers.
 */
import { requestJira } from '@forge/bridge';

export interface BackendContextIdentifiers {
    issueId?: string;
    projectId?: string;
}

const NUMERIC_ID = /^\d+$/;

/**
 * Budget for resolving a project identifier before a surface is allowed to
 * render without it. Matches the five-second budget the `permission-checking`
 * specification already fixes for reading the Forge context, rather than
 * introducing a second, unrelated bound.
 */
const PROJECT_ID_BUDGET_MS = 5_000;

let current: BackendContextIdentifiers = {};

const resolvedProjectIds = new Map<string, string | undefined>();

/**
 * Narrow a context value to a numeric Jira identifier rendered as a string.
 * Numbers are accepted and stringified; keys and synthetic values are dropped.
 */
function toNumericId(value: unknown): string | undefined {
    if (typeof value === 'number' && Number.isInteger(value) && value >= 0) {
        return String(value);
    }
    if (typeof value === 'string' && NUMERIC_ID.test(value.trim())) {
        return value.trim();
    }
    return undefined;
}

/**
 * Publish the identifiers for the current surface, replacing both of them: an
 * identifier omitted from the argument is cleared rather than retained, so a
 * surface always publishes the whole context it knows. Non-numeric values are
 * discarded rather than sent, so an unresolvable identifier omits its header
 * instead of producing a gateway denial.
 */
export function setBackendContext(identifiers: {
    issueId?: unknown;
    projectId?: unknown;
}): void {
    current = {
        issueId: toNumericId(identifiers.issueId),
        projectId: toNumericId(identifiers.projectId),
    };
}

/** The identifiers the transport should attach to the next backend request. */
export function getBackendContext(): BackendContextIdentifiers {
    return current;
}

/** Drops the published identifiers. Exists for test isolation. */
export function clearBackendContext(): void {
    current = {};
    resolvedProjectIds.clear();
}

interface JiraProjectResponse {
    id?: string | number;
}

/**
 * Resolve a project key to its numeric identifier via Jira, caching both hits
 * and misses so a project without a readable identifier is asked for once.
 * Used by surfaces that know only the project key, such as the project page.
 */
export async function resolveProjectId(
    projectKey: string,
): Promise<string | undefined> {
    const key = projectKey.trim();
    if (key === '') return undefined;
    if (resolvedProjectIds.has(key)) return resolvedProjectIds.get(key);

    let projectId: string | undefined;
    try {
        const response = await requestJira(
            `/rest/api/3/project/${encodeURIComponent(key)}`,
            { headers: { Accept: 'application/json' } },
        );
        if (response.ok) {
            const project = (await response.json()) as JiraProjectResponse;
            projectId = toNumericId(project.id);
        }
    } catch {
        projectId = undefined;
    }

    resolvedProjectIds.set(key, projectId);
    return projectId;
}

/**
 * Marks the budget elapsing before the Jira lookup answered, distinguishing a
 * timeout from a project that resolved to no identifier.
 */
const TIMED_OUT = Symbol('project-id-lookup-timed-out');

/**
 * Resolve within {@link PROJECT_ID_BUDGET_MS}, yielding no identifier if the
 * lookup outlives its budget so a surface is never blocked on Jira. The
 * in-flight lookup still populates the cache, so a later request picks up the
 * identifier once it arrives.
 *
 * A timeout is logged: it leaves `x-project-id` off every request from the
 * surface, which makes the gateway skip its permission check and answer 403
 * from each guarded endpoint. Naming the project key gives that otherwise
 * silent degradation a diagnostic.
 */
async function resolveProjectIdWithinBudget(
    projectKey: string,
): Promise<string | undefined> {
    const outcome = await Promise.race([
        resolveProjectId(projectKey),
        new Promise<typeof TIMED_OUT>((resolve) =>
            setTimeout(() => resolve(TIMED_OUT), PROJECT_ID_BUDGET_MS),
        ),
    ]);

    if (outcome === TIMED_OUT) {
        console.warn(
            `[backendContext] resolving the numeric project identifier for `
                + `${projectKey} exceeded its ${PROJECT_ID_BUDGET_MS}ms budget; `
                + `proceeding without the x-project-id header`,
        );
        return undefined;
    }

    return outcome;
}

/**
 * Publish the project identifier for a surface that knows its key, awaiting the
 * Jira lookup only when the identifier is not already known.
 *
 * Only the project identifier is published: a previously published issue
 * identifier is preserved rather than cleared, so a surface that publishes
 * issue context first and project context afterwards keeps sending
 * `x-issue-id`. Use {@link setBackendContext} to replace both identifiers.
 *
 * Surfaces await this before rendering, because a backend request issued without
 * `x-project-id` makes the gateway take its "no context headers" path, which
 * yields an empty permission set and a 403 from every guarded endpoint. An
 * unresolvable project settles with no identifier and its header omitted, which
 * is the specified degradation rather than a blocked surface.
 */
export async function publishProjectContext(
    projectId: unknown,
    projectKey: string,
): Promise<void> {
    const resolved =
        toNumericId(projectId)
        ?? (await resolveProjectIdWithinBudget(projectKey));

    current = { ...current, projectId: resolved };
}
