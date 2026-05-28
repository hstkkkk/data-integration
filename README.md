# 基于 XML 的异构数据集成 — 集成教务系统

三院（A/B/C）异构 DBMS 教务系统的数据集成项目。基于 XML + 自定义 Socket 协议，实现课程共享、跨院选课、跨院退课、全局统计、跨院「我的选课」聚合。

**数据库后端：** SQL Server (A) · Oracle (B) · MySQL (C)  
**通信：** 纯 Socket + 自定义文本帧 + XML 负载  
**格式转换：** XSLT (TrAX) + XSD (Xerces)  
**GUI：** Java Swing  

---

## 环境要求

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | 编译运行 |
| Maven | 3.8+ | 构建 |
| Docker | 20+ | SQL Server / Oracle / MySQL 容器 |

---

## 快速开始

### 首次运行

```bash
# 1. 克隆仓库
git clone <repo-url> && cd data-integration

# 2. 启动 3 个 DB 容器 + 构建 + 起 4 个 JVM
./scripts/start-all.sh
```

`start-all.sh` 会：
1. 启动 / 复用 SQL Server 2022、Oracle 23 Free、MySQL 8.0 容器
2. `mvn install -DskipTests` 构建全部模块
3. 后台启动 4 个 Java 进程（Integration + 3 院 server）

**注意**：脚本**不会**自动建表 / 灌种子数据。首次运行需手工执行下方「数据库初始化」一节，三院各跑一次。

### 启动客户端

```bash
# 学院 A（SQL Server）
java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) \
    client.Main --college=A --server=127.0.0.1:9001

# 学院 B（Oracle）
java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) \
    client.Main --college=B --server=127.0.0.1:9002

# 学院 C（MySQL）
java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) \
    client.Main --college=C --server=127.0.0.1:9003
```

### 停止

```bash
./scripts/stop-all.sh
```

停止所有 Java 进程和 Docker 容器。

---

## 测试账号

| 学院 | 账号前缀 | 密码 | 管理员 |
|------|---------|------|--------|
| A | `as001`–`as050` | `pwd001`–`pwd050` | `admin` / `admin1` |
| B | `bs001`–`bs050` | `pwd001`–`pwd050` | `admin` / `admin1` |
| C | `cs001`–`cs050` | `pwd001`–`pwd050` | `admin` / `admin1` |

---

## 端口映射

| 进程 | 端口 | 协议 |
|------|------|------|
| Integration Server | 9100 | Socket |
| College A Server | 9001 | Socket |
| College B Server | 9002 | Socket |
| College C Server | 9003 | Socket |
| SQL Server (Docker) | 1433 | JDBC |
| Oracle (Docker) | 1521 | JDBC |
| MySQL (Docker) | 3306 | JDBC |

---

## 数据库初始化（首次必跑）

`start-all.sh` 不会自动建表 / 灌种子。首次必须手工执行下方三段（每院一段）。后续重启不需要重灌（schema 已持久化在容器卷里）。

```bash
# A 院 — SQL Server
docker cp college-a/src/main/resources/sql/init_a.sql      di-sqlserver:/tmp/init_a.sql
docker cp college-a/src/main/resources/sql/init_a_data.sql di-sqlserver:/tmp/init_a_data.sql
docker exec di-sqlserver /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U SA -P 'Di_Strong_Pwd!2024' -No -i /tmp/init_a.sql
docker exec di-sqlserver /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U SA -P 'Di_Strong_Pwd!2024' -No -d collegeA -i /tmp/init_a_data.sql

# B 院 — Oracle
# 首次：CDB 启动后 PDB 通常已 OPEN；若手工 docker start 容器后报 ORA-01109，先打开 PDB：
#   docker exec di-oracle sqlplus -s system/oracle123 <<<'ALTER PLUGGABLE DATABASE freepdb1 OPEN;'
docker cp college-b/src/main/resources/sql/init_b.sql      di-oracle:/tmp/init_b.sql
docker cp college-b/src/main/resources/sql/init_b_data.sql di-oracle:/tmp/init_b_data.sql
docker exec di-oracle bash -c \
    "NLS_LANG=.AL32UTF8 sqlplus -s system/oracle123@freepdb1 @/tmp/init_b.sql"
docker exec di-oracle bash -c \
    "NLS_LANG=.AL32UTF8 sqlplus -s collegeb/collegeb@freepdb1 @/tmp/init_b_data.sql"

# C 院 — MySQL
docker cp college-c/src/main/resources/sql/init_c.sql      di-mysql:/tmp/init_c.sql
docker cp college-c/src/main/resources/sql/init_c_data.sql di-mysql:/tmp/init_c_data.sql
docker exec di-mysql sh -c \
    "mysql -uroot -pmysql123 --default-character-set=utf8mb4 < /tmp/init_c.sql"
docker exec di-mysql sh -c \
    "mysql -uroot -pmysql123 --default-character-set=utf8mb4 collegeC < /tmp/init_c_data.sql"
```

灌完后 `./scripts/stop-all.sh && ./scripts/start-all.sh` 重启 server 让其拿到新数据。

---

## 运行测试

```bash
# 全部单元测试（~146 个，不需要 Docker）
mvn test

# 集成测试（需要对应数据库运行中）
mvn -pl college-a test -Dtest="AccountDaoIT,StudentDaoIT,CourseDaoIT,ChoiceDaoIT"
```

---

## 项目结构

```
data-integration/
├── common/             # 公共层：协议帧、Command、XmlIO、XsdValidator、XsltTransformer、统一 XSD
├── college-a/          # 学院 A（SQL Server）：DAO、XML Adapter、XSD、Server + 10 handler
├── college-b/          # 学院 B（Oracle）：同上
├── college-c/          # 学院 C（MySQL）：同上
├── integration/        # 集成 Server：CollegeClient、跨院路由、XSL 转换、聚合统计
├── client/             # Swing 统一客户端（--college=A|B|C）
├── scripts/
│   ├── start-all.sh    # 一键启动 3 DB + 4 JVM
│   ├── stop-all.sh     # 停止全部进程
│   ├── db/             # Docker 启动脚本（SQL Server / Oracle / MySQL）
│   └── seed-data/      # 种子数据生成器（SeedA / SeedB / SeedC）
└── docs/
    ├── demo.md         # 演示脚本
    ├── report.md       # 实验报告
    └── superpowers/    # 设计 spec + 实现 plan
```

---

## 核心流程

```
客户端 → 本院 Server → [跨院判断]
  ├── 本院课程 → 本院 DAO（SQL Server / Oracle / MySQL）
  └── 跨院课程 → Integration Server → XSL 格式转换 → 目标院 Server → 目标院 DAO

课程共享：LIST_SHARED_COURSES → Integration 向 A/B/C（除自己）拉取 → formatX.xsl 统一 → 合并 → unifiedToX.xsl 返回
跨院选课：ENROLL → 前缀检测（AC/BC/CC）→ CROSS_ENROLL → APPLY_CHOICE → 目标院写选课表（带 来源 列）
跨院退课：WITHDRAW → 同上 → CROSS_WITHDRAW → REVOKE_CHOICE → 目标院删除
全局统计：STATS_GLOBAL → Integration 向三院 STATS_PULL → 聚合 → 返回报表
我的选课：LIST_MY_CHOICES → 本院 join + 转发 PULL_MY_CHOICES → Integration fan-out 三院 ASK_MY_CHOICES → unifiedMyChoiceToX.xsl 翻成本院字段
```

---

## 异构差异（设计上必须保留）

| 维度 | 学院 A | 学院 B | 学院 C |
|------|--------|--------|--------|
| DBMS | SQL Server | Oracle | MySQL |
| 表命名 | 中文长名（课程编号） | 中文短名（编号） | 英文缩写（Cno） |
| 学生表字段 | 学号/姓名/性别/院系/关联账户 | 学号/姓名/性别/专业/密码 | Sno/Snm/Sex/Sde/Pwd |
| 账户认证 | 账户名/密码/权限 | 账户名/密码/级别 | acc/passwd/CreateDate |
| 选课表约束 | UNIQUE(课程编号,学生编号) | 无唯一约束 | UNIQUE(Sno,Cno) |
| 课程编号前缀 | AC | BC | CC |
| 学生编号前缀 | AS | BS | CS |

> 三院 `选课` 表都额外有 `来源` / `Org` 列（默认本院字符 'A'/'B'/'C'），跨院选课时记录请求方学院；选课表对学生表**没有 FK**——跨院学生不在本院学生表里，外键约束会破坏跨院流。

---

## 故障排查

| 现象 | 多半原因 | 处理 |
|------|---------|------|
| `ERROR: port 9001 already in use` | 上次 stop-all 没杀干净 / pid 文件丢失 | 再跑一次 `./scripts/stop-all.sh`（已加按端口兜底清理）。或手工：`for p in 9001 9002 9003 9100; do lsof -tiTCP:$p -sTCP:LISTEN \| xargs -r kill; done` |
| `ORA-01109: database not open` | Oracle 容器重启后 PDB 没自动 OPEN | `docker exec di-oracle sqlplus -s system/oracle123 <<<'ALTER PLUGGABLE DATABASE freepdb1 OPEN;'` |
| `ERROR 1064 ... near '��课'` 灌 MySQL | docker exec 默认 charset 把中文表名当 latin1 | 灌 SQL 时加 `--default-character-set=utf8mb4`（README 已用），或用 `docker cp` 进容器后在容器内执行 |
| 选共享课报 `cvc-datatype-valid: '4.0' is not a valid value for 'integer'` | 旧版 XSD 把 `score`/`time` 定为 `xs:unsignedByte` | 已修（commit 1b0427a），重启 server 即可 |
| `加载共享课程失败: load xsl: /xsl/BtoA.xsl` | XSL 在 integration 模块但 college server classpath 没含它 | 已修，每院的 `unifiedToX.xsl` 已迁到对应 college 模块 |
| `选课失败: APPLY_FAILED detail: enroll failed` 但「我的选课」能看到这门课 | 旧版 B 院 `ChoiceDao` 在 autoCommit=true 连接上调 `c.commit()`，INSERT 已 autocommit、commit() 抛异常 | 已修（commit 删了 redundant commit） |
