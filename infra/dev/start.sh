#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 9.8.1 is required. See README.md." >&2
  exit 1
fi
if [ ! -f apps/web/package-lock.json ]; then
  echo "The pinned npm lockfile is missing; do not install without it." >&2
  exit 1
fi
if [ ! -d apps/web/node_modules ]; then
  echo "First run: npm --prefix apps/web ci --ignore-scripts" >&2
  exit 1
fi

gradle -p services/api bootRun &
api_pid=$!
npm --prefix apps/web run dev &
web_pid=$!

cleanup() {
  kill "$api_pid" "$web_pid" 2>/dev/null || true
  wait "$api_pid" "$web_pid" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
wait -n "$api_pid" "$web_pid"
