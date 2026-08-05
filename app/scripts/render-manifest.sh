#!/usr/bin/env bash
#
# Substitutes deployment origins into app/manifest.yml in place.
#
# Why this exists instead of Forge's own `${...}` interpolation:
# `environment.variables` is a documented manifest feature and every local
# validator accepts it, but Forge's server-side pre-deployment check reads the
# manifest as raw bytes and rejects unresolved variables in egress-reachable
# fields:
#
#   Invalid URL detected for EGRESS permissions object:
#   ${SMISKI_API_BASE_URL}, ${SMISKI_API_BASE_URL}   MANIFEST_INVALID_RULE
#
# The variable is reported twice for a single `remotes[].baseUrl` occurrence,
# because the server also resolves the `- remote: meet-backend` entry under
# `permissions.external.fetch` back to that same `baseUrl`. Neither the
# indirection nor `baseUrl` itself is exempt, so the file on disk must already
# hold a real URL before the CLI reads it.
#
# Usage (from app/, before any forge lint/deploy/install):
#   SMISKI_API_BASE_URL=https://gateway.example ./scripts/render-manifest.sh
#
# Idempotent: re-running once the placeholder is gone is a no-op. The rendered
# manifest must never be committed; `git checkout app/manifest.yml` restores it.

set -euo pipefail

readonly PLACEHOLDER='https://render-manifest-not-run.invalid'
readonly MANIFEST="${1:-manifest.yml}"

if [[ ! -f ${MANIFEST} ]]; then
    echo "error: ${MANIFEST} not found; run this from the app/ directory." >&2
    exit 1
fi

if ! grep -q "${PLACEHOLDER}" "${MANIFEST}"; then
    echo "${MANIFEST} already rendered; nothing to do."
    exit 0
fi

if [[ -z ${SMISKI_API_BASE_URL:-} ]]; then
    echo "error: SMISKI_API_BASE_URL is not set." >&2
    exit 1
fi

# Forge accepts only https:// (or wss://) origins with no trailing slash, and a
# malformed value fails server-side with the same opaque MANIFEST_INVALID_RULE
# this script exists to avoid. Reject it here, where the message is actionable.
if [[ ! ${SMISKI_API_BASE_URL} =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?$ ]]; then
    echo "error: SMISKI_API_BASE_URL must be an https:// origin with no path" >&2
    echo "       or trailing slash; got '${SMISKI_API_BASE_URL}'." >&2
    exit 1
fi

python3 - "${MANIFEST}" "${PLACEHOLDER}" "${SMISKI_API_BASE_URL}" << 'PY'
import pathlib
import sys

manifest, placeholder, value = sys.argv[1], sys.argv[2], sys.argv[3]
path = pathlib.Path(manifest)
path.write_text(path.read_text(encoding='utf-8').replace(placeholder, value), encoding='utf-8')
PY

echo "Rendered ${MANIFEST} with SMISKI_API_BASE_URL=${SMISKI_API_BASE_URL}"
