#!/usr/bin/env bash
# scripts/db/start-mysql.sh
set -e
NAME=di-mysql
PASSWORD='mysql123'

if docker ps -a --format '{{.Names}}' | grep -q "^${NAME}$"; then
  docker start "${NAME}"
else
  docker run -e "MYSQL_ROOT_PASSWORD=${PASSWORD}" \
    -p 3306:3306 --name "${NAME}" \
    -d mysql:8.0
fi

echo "Waiting for MySQL..."
for i in {1..30}; do
  docker exec "${NAME}" mysql -uroot -p"${PASSWORD}" -e "SELECT 1" >/dev/null 2>&1 && break
  sleep 2
done
echo "MySQL up at localhost:3306 (root / ${PASSWORD})"
