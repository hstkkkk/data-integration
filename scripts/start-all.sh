#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p logs

# Refuse to start if any target port is still occupied by a previous run.
check_port_free() {
  local port="$1"
  if lsof -iTCP:"$port" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    echo "ERROR: port $port already in use. Run ./scripts/stop-all.sh first." >&2
    lsof -iTCP:"$port" -sTCP:LISTEN -n -P >&2
    exit 1
  fi
}
for p in 9001 9002 9003 9100; do check_port_free "$p"; done

echo "[1/5] Starting databases (Docker) ..."
./scripts/db/start-sqlserver.sh
./scripts/db/start-oracle.sh
./scripts/db/start-mysql.sh

echo "[2/5] Building jars (skip tests) ..."
if ! mvn -DskipTests install; then
  echo "ERROR: maven build failed; refusing to start servers with stale classes." >&2
  exit 1
fi

echo "[3/5] Generating classpath files ..."
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -pl integration
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -pl college-a
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -pl college-b
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -pl college-c
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -DincludeScope=runtime -pl client

echo "[4/5] Starting servers ..."
java -Dport=9100 -cp integration/target/classes:common/target/classes:$(cat integration/target/classpath.txt) \
    integration.server.IntegrationServer >logs/integration.log 2>&1 &
echo $! > logs/integration.pid

java -Dport=9001 -cp college-a/target/classes:common/target/classes:$(cat college-a/target/classpath.txt) \
    college.a.server.CollegeAServer >logs/college-a.log 2>&1 &
echo $! > logs/college-a.pid

java -Dport=9002 -cp college-b/target/classes:common/target/classes:$(cat college-b/target/classpath.txt) \
    college.b.server.CollegeBServer >logs/college-b.log 2>&1 &
echo $! > logs/college-b.pid

java -Dport=9003 -cp college-c/target/classes:common/target/classes:$(cat college-c/target/classpath.txt) \
    college.c.server.CollegeCServer >logs/college-c.log 2>&1 &
echo $! > logs/college-c.pid

echo "[5/5] Verifying servers came up ..."
verify_listening() {
  local name="$1" port="$2" pidfile="$3"
  for _ in {1..30}; do
    if lsof -iTCP:"$port" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
      echo "  $name :$port OK"
      return 0
    fi
    if [[ -f "$pidfile" ]] && ! kill -0 "$(cat "$pidfile")" 2>/dev/null; then
      echo "ERROR: $name died during startup. Tail of log:" >&2
      tail -n 20 "logs/$name.log" >&2 || true
      exit 1
    fi
    sleep 0.5
  done
  echo "ERROR: $name :$port did not start listening within 15s. Tail of log:" >&2
  tail -n 20 "logs/$name.log" >&2 || true
  exit 1
}
verify_listening integration 9100 logs/integration.pid
verify_listening college-a   9001 logs/college-a.pid
verify_listening college-b   9002 logs/college-b.pid
verify_listening college-c   9003 logs/college-c.pid

echo ""
echo "All servers started. Logs in logs/."
echo "To start clients:"
echo '  java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) client.Main --college=A --server=127.0.0.1:9001'
echo '  java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) client.Main --college=B --server=127.0.0.1:9002'
echo '  java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) client.Main --college=C --server=127.0.0.1:9003'
