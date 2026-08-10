#!/bin/sh
set -e

if [ -n "$CI" ]; then
    pnpm exec forge settings set usage-analytics false > /dev/null 2>&1 || true
fi

exec pnpm exec forge "$@"
