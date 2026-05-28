#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Detect the host platform once and keep the platform-specific branches explicit.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) PLATFORM='windows' ;;
  Darwin*) PLATFORM='macos' ;;
  Linux*) PLATFORM='linux' ;;
  *) PLATFORM='unknown' ;;
esac

listener_pids() {
  local port="$1"
  case "$PLATFORM" in
    windows)
      netstat -ano 2>/dev/null | awk -v needle=":$port" '
        index($0, needle) && $0 ~ /LISTENING/ { print $NF }
      ' | sort -u
      ;;
    macos|linux|unknown)
      command -v lsof >/dev/null 2>&1 &&
          lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
      ;;
  esac
}

stop_pid() {
  local pid="$1"
  if [ -z "$pid" ]; then
    return 0
  fi
  if [ "$PLATFORM" = "windows" ]; then
    taskkill.exe /PID "$pid" /T /F >/dev/null 2>&1 || kill "$pid" 2>/dev/null || true
  else
    kill "$pid" 2>/dev/null || true
  fi
}

# 1) Kill by PID file (the happy path).
for pidf in logs/integration.pid logs/college-a.pid logs/college-b.pid logs/college-c.pid; do
  if [ -f "$pidf" ]; then
    stop_pid "$(cat "$pidf")"
    rm -f "$pidf"
  fi
done

# 2) Fallback: any java listener still on our ports is an orphan from a
#    previous run whose pidfile was lost. Kill it too so the next
#    start-all does not hit "port already in use".
for port in 9001 9002 9003 9100; do
  pids="$(listener_pids "$port")"
  if [ -n "$pids" ]; then
    echo "Stopping orphan listener on :$port (pid(s): $pids)"
    # SIGTERM first; if anything is still alive after 1s, SIGKILL it.
    for pid in $pids; do stop_pid "$pid"; done
    sleep 1
    still="$(listener_pids "$port")"
    if [ -n "$still" ]; then
      if [ "$PLATFORM" = "windows" ]; then
        for pid in $still; do taskkill.exe /PID "$pid" /T /F >/dev/null 2>&1 || true; done
      else
        kill -9 $still 2>/dev/null || true
      fi
    fi
  fi
done

docker stop di-sqlserver 2>/dev/null || true
docker stop di-oracle 2>/dev/null || true
docker stop di-mysql 2>/dev/null || true
echo "Stopped."
