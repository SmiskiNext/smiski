#!/bin/sh
set -e

if [ -n "$CI" ]; then
    forge settings set usage-analytics false > /dev/null 2>&1 || true
fi

exec forge "$@"
