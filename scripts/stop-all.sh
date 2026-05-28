#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# 1) Kill by PID file (the happy path).
for pidf in logs/integration.pid logs/college-a.pid logs/college-b.pid logs/college-c.pid; do
  if [ -f "$pidf" ]; then
    kill "$(cat "$pidf")" 2>/dev/null || true
    rm -f "$pidf"
  fi
done

# 2) Fallback: any java listener still on our ports is an orphan from a
#    previous run whose pidfile was lost. Kill it too so the next
#    start-all does not hit "port already in use".
for port in 9001 9002 9003 9100; do
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [ -n "$pids" ]; then
    echo "Stopping orphan listener on :$port (pid(s): $pids)"
    # SIGTERM first; if anything is still alive after 1s, SIGKILL it.
    kill $pids 2>/dev/null || true
    sleep 1
    still="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [ -n "$still" ]; then
      kill -9 $still 2>/dev/null || true
    fi
  fi
done

docker stop di-sqlserver 2>/dev/null || true
docker stop di-oracle 2>/dev/null || true
docker stop di-mysql 2>/dev/null || true
echo "Stopped."
