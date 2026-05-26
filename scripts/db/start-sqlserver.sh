#!/usr/bin/env bash
# scripts/db/start-sqlserver.sh
set -e
NAME=di-sqlserver
PASSWORD='Di_Strong_Pwd!2024'

if docker ps -a --format '{{.Names}}' | grep -q "^${NAME}$"; then
  docker start "${NAME}"
else
  docker run -e "ACCEPT_EULA=Y" -e "MSSQL_SA_PASSWORD=${PASSWORD}" \
    -p 1433:1433 --name "${NAME}" \
    -d mcr.microsoft.com/mssql/server:2022-latest
fi

echo "Waiting for SQL Server..."
for i in {1..120}; do
  docker exec "${NAME}" /opt/mssql-tools18/bin/sqlcmd -S localhost \
       -U SA -P "${PASSWORD}" -No -Q "SELECT 1" >/dev/null 2>&1 && break
  sleep 2
done
echo "SQL Server up at localhost:1433 (SA / ${PASSWORD})"
