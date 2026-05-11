#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/.." || exit 1
(cd server-java && ./mvnw -DskipTests package)
npm run build --prefix client-vue
