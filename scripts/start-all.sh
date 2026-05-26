#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p logs
echo "[1/3] Starting SQL Server (Docker) ..."
./scripts/db/start-sqlserver.sh

echo "[2/3] Building jars (skip tests) ..."
mvn -q -DskipTests package

echo "[3/3] Starting Integration Server and College A Server ..."
# Generate classpath files
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -pl integration
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -pl college-a

java -Dport=9100 -cp integration/target/classes:common/target/classes:$(cat integration/target/classpath.txt) \
    integration.server.IntegrationServer >logs/integration.log 2>&1 &
echo $! > logs/integration.pid

java -Dport=9001 -cp college-a/target/classes:common/target/classes:$(cat college-a/target/classpath.txt) \
    college.a.server.CollegeAServer >logs/college-a.log 2>&1 &
echo $! > logs/college-a.pid

echo "Servers started. Logs in logs/."
echo "To start client:"
echo "  java -cp client/target/classes:common/target/classes client.Main --college=A --server=127.0.0.1:9001"
