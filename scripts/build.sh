#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/.." || exit 1
npm run build:server
npm run build:client
