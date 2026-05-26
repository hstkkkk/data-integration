#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

for pidf in logs/integration.pid logs/college-a.pid logs/college-b.pid logs/college-c.pid; do
  if [ -f "$pidf" ]; then
    kill "$(cat "$pidf")" 2>/dev/null || true
    rm -f "$pidf"
  fi
done

docker stop di-sqlserver 2>/dev/null || true
docker stop di-oracle 2>/dev/null || true
docker stop di-mysql 2>/dev/null || true
echo "Stopped."
