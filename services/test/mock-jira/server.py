"""Mock of the Jira Cloud bulk permission check, for the load-test stack.

Replaces the single external dependency on the measured request path. The Go
gateway calls Jira on every cache miss with a 2 second timeout
(services/gateway/internal/jira/client.go), which alone exceeds TC-04's 500 ms
threshold and makes results depend on an external network. Pointing
JIRA_API_BASE at this service removes that, so a cold-cache measurement reports
the stack's own latency rather than Atlassian's.

Endpoint, matching the URL the gateway builds:

    POST /ex/jira/{cloudId}/rest/api/3/permissions/check

Every requested permission is answered as GRANTED. The response ECHOES the
identifiers from the request rather than declaring its own, which is load
bearing: the gateway asks for
`ari:cloud:ecosystem::extension/{appId}/{environmentId}/static/view-meeting`,
builds that prefix from claims in the caller's own token, and discards any
grant that does not match it
(`mapARIsToBareKeys` in internal/authz/service.go). A mock returning fixed ARIs
would therefore have its answers silently dropped for every token whose app or
environment identifier differed, and the symptom would be an empty
x-project-permissions header rather than an error. Echoing makes the mock
correct for any token the harness signs.

Both `globalPermissions` and `projectPermissions` are always present in the
response body. The gateway rejects a payload where either is null as
"malformed jira response: missing required members", and an omitted JSON member
decodes to null.

SECURITY: this grants every permission asked of it to every caller, with no
authentication. It exists to make measurement independent of the network and is
local-only, like the rest of services/test/.
"""

import json
import os
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PERMISSION_CHECK_PATH = re.compile(
    r"^/ex/jira/(?P<cloud_id>[^/]+)/rest/api/3/permissions/check$"
)

HEALTH_PATH = "/healthz"

MAX_REQUEST_BYTES = 1 << 20


class MockJiraHandler(BaseHTTPRequestHandler):
    """Answers the bulk permission check and nothing else.

    Any other path returns 404 so a mistyped base URL surfaces as a clear
    failure instead of an empty permission set that looks like a denial.
    """

    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:
        if self.path == HEALTH_PATH:
            self._respond(200, {"status": "ok"})
            return

        self._respond(404, {"errorMessages": [f"no such path: {self.path}"]})

    def do_POST(self) -> None:
        match = PERMISSION_CHECK_PATH.match(self.path)
        if match is None:
            self._respond(
                404, {"errorMessages": [f"no such path: {self.path}"]}
            )
            return

        request_body = self._read_body()
        if request_body is None:
            return

        self._respond(200, grant_everything_requested(request_body))

    def _read_body(self) -> dict | None:
        """Decodes the request body, answering 400 when it is unusable."""
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._respond(400, {"errorMessages": ["invalid Content-Length"]})
            return None

        if content_length > MAX_REQUEST_BYTES:
            self._respond(400, {"errorMessages": ["request body too large"]})
            return None

        raw = self.rfile.read(content_length) if content_length else b"{}"

        try:
            decoded = json.loads(raw or b"{}")
        except json.JSONDecodeError as error:
            self._respond(
                400, {"errorMessages": [f"invalid JSON body: {error}"]}
            )
            return None

        if not isinstance(decoded, dict):
            self._respond(
                400, {"errorMessages": ["request body must be a JSON object"]}
            )
            return None

        return decoded

    def _respond(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args) -> None:
        """Silences the default per-request stderr line.

        TC-04 issues 500 requests inside one second; logging each one makes the
        mock's own I/O part of what is being measured.
        """


def grant_everything_requested(request_body: dict) -> dict:
    """Builds a BulkPermissionGrants granting every permission asked for.

    The issue and project scope of each request entry is carried through onto
    the matching grant, mirroring the real API's response shape.
    """
    granted: list[dict] = []

    for entry in request_body.get("projectPermissions") or []:
        if not isinstance(entry, dict):
            continue

        issues = entry.get("issues") or []
        projects = entry.get("projects") or []

        for permission in entry.get("permissions") or []:
            grant: dict = {"permission": permission}
            if issues:
                grant["issues"] = issues
            if projects:
                grant["projects"] = projects
            granted.append(grant)

    return {
        "globalPermissions": list(request_body.get("globalPermissions") or []),
        "projectPermissions": granted,
    }


def main() -> None:
    port = int(os.environ.get("MOCK_JIRA_PORT", "8080"))
    server = ThreadingHTTPServer(("0.0.0.0", port), MockJiraHandler)

    print(f"mock-jira listening on :{port}", file=sys.stderr, flush=True)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.shutdown()


if __name__ == "__main__":
    main()
