# Plan 1 Smoke Test Checklist (Manual)

## Prerequisites
- Docker running
- `mvn -DskipTests package` and `seed.SeedA` executed
- SQL Server schema initialized (init_a.sql)

## Steps
1. `./scripts/start-all.sh` — confirm logs/ files have "listening on" lines
2. Start client: `java -cp client/target/classes:common/target/classes client.Main --college=A --server=127.0.0.1:9001`
3. Login with account `as001` / password `pwd001` — should jump to course list window
4. Click "刷新本院课程" — should see 10 rows
5. Try wrong password `xxx` — should see "AUTH_FAILED"
6. Via `nc 127.0.0.1 9100` send PING command (manually construct frame), receive `<pong>integration-server</pong>`
7. Close windows; `./scripts/stop-all.sh`
