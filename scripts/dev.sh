#!/usr/bin/env sh
cd "$(dirname "$0")/.." || exit 1
exec npx concurrently -k -n server,client -c blue,green "npm run dev:server" "npm run dev:client"
