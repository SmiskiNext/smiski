/**
 * inject-code-samples.ts
 *
 * Reads ../services/openapi.yaml, injects x-codeSamples (curl / TypeScript / Java)
 * into every operation, and writes the enriched spec to ./openapi-with-samples.yaml.
 *
 * The source file is never modified.
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

// ── Path resolution ───────────────────────────────────────────────────────────

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DOCS_DIR = path.resolve(__dirname, '..');
const SOURCE_SPEC = path.resolve(DOCS_DIR, '..', 'services', 'openapi.yaml');
const OUTPUT_SPEC = path.resolve(DOCS_DIR, 'openapi-with-samples.yaml');

// ── Types ─────────────────────────────────────────────────────────────────────

interface CodeSample {
    lang: string;
    label: string;
    source: string;
}

interface OpenApiOperation {
    operationId?: string;
    summary?: string;
    requestBody?: {
        content?: Record<string, unknown>;
        required?: boolean;
    };
    parameters?: Array<{
        name: string;
        in: string;
        required?: boolean;
        schema?: { type?: string; format?: string };
        example?: unknown;
    }>;
    'x-codeSamples'?: CodeSample[];
}

interface OpenApiSpec {
    servers?: Array<{ url: string }>;
    paths?: Record<string, Record<string, OpenApiOperation>>;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const HTTP_METHODS = [
    'get',
    'post',
    'put',
    'patch',
    'delete',
    'head',
    'options',
] as const;

/** Resolve the base URL from the spec servers block, fallback to localhost. */
function resolveBaseUrl(spec: OpenApiSpec): string {
    return spec.servers?.[0]?.url ?? 'http://localhost:8080';
}

/**
 * Build a concrete example URL by replacing path params with their example
 * values or a descriptive placeholder.
 */
function buildExampleUrl(urlPath: string, op: OpenApiOperation): string {
    let resolved = urlPath;
    for (const param of op.parameters ?? []) {
        if (param.in !== 'path') continue;
        const value =
            param.example
            ?? (param.name === 'version' ? '1' : `{${param.name}}`);
        resolved = resolved.replace(`{${param.name}}`, String(value));
    }
    return resolved;
}

/** True when the operation has a JSON request body. */
function hasJsonBody(op: OpenApiOperation): boolean {
    return !!op.requestBody?.content?.['application/json'];
}

// ── Code sample generators ────────────────────────────────────────────────────

function buildCurlSample(
    method: string,
    baseUrl: string,
    urlPath: string,
    op: OpenApiOperation,
): CodeSample {
    const url = baseUrl + buildExampleUrl(urlPath, op);
    const methodUpper = method.toUpperCase();

    const headers = [
        `-H 'Content-Type: application/json'`,
        `-H 'X-Tenant-ID: <your-tenant-id>'`,
        `-H 'X-Account-Id: <your-account-id>'`,
    ];

    const bodyFlag = hasJsonBody(op) ? ` \\\n  -d '{}'` : '';

    const source =
        `curl -X ${methodUpper} '${url}' \\\n`
        + headers.map((h) => `  ${h}`).join(' \\\n')
        + bodyFlag;

    return { lang: 'curl', label: 'cURL', source };
}

function buildTypeScriptSample(
    method: string,
    baseUrl: string,
    urlPath: string,
    op: OpenApiOperation,
): CodeSample {
    const examplePath = buildExampleUrl(urlPath, op);
    const methodUpper = method.toUpperCase();
    const hasBody = hasJsonBody(op);

    const bodyLines = hasBody
        ? [
              `  const body = JSON.stringify({`,
              `    // TODO: fill in request body`,
              `  });`,
              ``,
          ]
        : [];

    const fetchOptions = [
        `    method: '${methodUpper}',`,
        `    headers: {`,
        `      'Content-Type': 'application/json',`,
        `      'X-Tenant-ID': '<your-tenant-id>',`,
        `      'X-Account-Id': '<your-account-id>',`,
        `    },`,
        ...(hasBody ? [`    body,`] : []),
    ];

    const source = [
        `const BASE_URL = '${baseUrl}';`,
        ``,
        `async function ${op.operationId ?? 'request'}() {`,
        ...bodyLines,
        `  const response = await fetch(\`\${BASE_URL}${examplePath}\`, {`,
        ...fetchOptions,
        `  });`,
        ``,
        `  if (!response.ok) {`,
        `    throw new Error(\`HTTP error: \${response.status}\`);`,
        `  }`,
        ``,
        `  return response.json();`,
        `}`,
    ].join('\n');

    return { lang: 'typescript', label: 'TypeScript', source };
}

function buildJavaSample(
    method: string,
    baseUrl: string,
    urlPath: string,
    op: OpenApiOperation,
): CodeSample {
    const examplePath = buildExampleUrl(urlPath, op);
    const methodUpper = method.toUpperCase();
    const hasBody = hasJsonBody(op);

    const bodyLines = hasBody
        ? [
              `        String body = "{}"; // TODO: fill in request body`,
              `        request = HttpRequest.newBuilder()`,
              `                .uri(URI.create(BASE_URL + "${examplePath}"))`,
              `                .header("Content-Type", "application/json")`,
              `                .header("X-Tenant-ID", "<your-tenant-id>")`,
              `                .header("X-Account-Id", "<your-account-id>")`,
              `                .method("${methodUpper}", HttpRequest.BodyPublishers.ofString(body))`,
              `                .build();`,
          ]
        : [
              `        request = HttpRequest.newBuilder()`,
              `                .uri(URI.create(BASE_URL + "${examplePath}"))`,
              `                .header("Content-Type", "application/json")`,
              `                .header("X-Tenant-ID", "<your-tenant-id>")`,
              `                .header("X-Account-Id", "<your-account-id>")`,
              `                .${methodUpper === 'GET' ? 'GET()' : `method("${methodUpper}", HttpRequest.BodyPublishers.noBody())`}`,
              `                .build();`,
          ];

    const source = [
        `import java.net.URI;`,
        `import java.net.http.HttpClient;`,
        `import java.net.http.HttpRequest;`,
        `import java.net.http.HttpResponse;`,
        ``,
        `public class SmiskiApiExample {`,
        `    private static final String BASE_URL = "${baseUrl}";`,
        ``,
        `    public static void main(String[] args) throws Exception {`,
        `        HttpClient client = HttpClient.newHttpClient();`,
        `        HttpRequest request;`,
        ``,
        ...bodyLines,
        ``,
        `        HttpResponse<String> response = client.send(`,
        `                request, HttpResponse.BodyHandlers.ofString());`,
        ``,
        `        System.out.println("Status: " + response.statusCode());`,
        `        System.out.println("Body:   " + response.body());`,
        `    }`,
        `}`,
    ].join('\n');

    return { lang: 'java', label: 'Java', source };
}

// ── Main ──────────────────────────────────────────────────────────────────────

function injectCodeSamples(spec: OpenApiSpec): OpenApiSpec {
    const baseUrl = resolveBaseUrl(spec);
    const paths = spec.paths ?? {};

    for (const [urlPath, pathItem] of Object.entries(paths)) {
        for (const method of HTTP_METHODS) {
            const op = pathItem[method] as OpenApiOperation | undefined;
            if (!op) continue;

            // Skip if x-codeSamples already defined (allow manual overrides)
            if (op['x-codeSamples']) continue;

            op['x-codeSamples'] = [
                buildCurlSample(method, baseUrl, urlPath, op),
                buildTypeScriptSample(method, baseUrl, urlPath, op),
                buildJavaSample(method, baseUrl, urlPath, op),
            ];
        }
    }

    return spec;
}

function main() {
    console.log(`Reading spec from: ${SOURCE_SPEC}`);
    const raw = fs.readFileSync(SOURCE_SPEC, 'utf8');
    const spec = yaml.load(raw) as OpenApiSpec;

    const enriched = injectCodeSamples(spec);

    const output = yaml.dump(enriched, {
        lineWidth: 120,
        noRefs: true,
        quoteStyle: 'double',
    });

    fs.writeFileSync(OUTPUT_SPEC, output, 'utf8');
    console.log(`Written enriched spec to: ${OUTPUT_SPEC}`);
}

main();
