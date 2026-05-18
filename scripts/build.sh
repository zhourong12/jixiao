#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/.." || exit 1
node scripts/package.mjs
