#!/usr/bin/env bash
# scripts/db/start-oracle.sh
set -e
NAME=di-oracle
PASSWORD='oracle123'

if docker ps -a --format '{{.Names}}' | grep -q "^${NAME}$"; then
  docker start "${NAME}"
else
  docker run -e "ORACLE_PASSWORD=${PASSWORD}" \
    -p 1521:1521 --name "${NAME}" \
    -d gvenzl/oracle-free:23-slim
fi

echo "Waiting for Oracle..."
for i in {1..60}; do
  docker exec "${NAME}" sqlplus -s system/"${PASSWORD}"@freepdb1 <<< "SELECT 1 FROM DUAL" >/dev/null 2>&1 && break
  sleep 3
done
echo "Oracle up at localhost:1521 (system / ${PASSWORD}, service=freepdb1)"
