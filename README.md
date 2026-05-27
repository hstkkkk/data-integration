# 基于 XML 的异构数据集成 — 集成教务系统

三院（A/B/C）异构 DBMS 教务系统的数据集成项目。基于 XML + 自定义 Socket 协议，实现课程共享、跨院选课、跨院退课、全局统计。

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

# 2. 拉取 Docker 镜像 + 构建 + 启动全部进程（5 分钟）
./scripts/start-all.sh
```

首次运行时 `start-all.sh` 会：
1. 拉取 SQL Server 2022、Oracle 23 Free、MySQL 8.0 镜像
2. 创建数据库并灌入种子数据（每院 50 学生 / 10 课程 / 每生 5 选课）
3. `mvn install -DskipTests` 构建全部 jar
4. 后台启动 4 个 Java 进程

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

## 数据库初始化（手工）

如果 `start-all.sh` 首次运行容器已启动但 schema 未建（Docker 拉镜像时超时），可手工执行：

```bash
# A 院 — SQL Server
docker exec -i di-sqlserver /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U SA -P 'Di_Strong_Pwd!2024' -No \
    < college-a/src/main/resources/sql/init_a.sql

docker exec -i di-sqlserver /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U SA -P 'Di_Strong_Pwd!2024' -No -d collegeA \
    < college-a/src/main/resources/sql/init_a_data.sql

# B 院 — Oracle（注意：需用 sysdba 连，Oracle listener 可能不稳定）
printf "ALTER SESSION SET CONTAINER=freepdb1;\nALTER SESSION SET CURRENT_SCHEMA=collegeb;\n" > /tmp/init_b_run.sql
cat college-b/src/main/resources/sql/init_b_data.sql >> /tmp/init_b_run.sql

sudo docker exec -i di-oracle sqlplus -s / as sysdba @/dev/stdin \
    < college-b/src/main/resources/sql/init_b.sql

sudo docker exec -i di-oracle sqlplus -s / as sysdba < /tmp/init_b_run.sql

# C 院 — MySQL
docker exec -i di-mysql mysql -uroot -pmysql123 --default-character-set=utf8mb4 \
    < college-c/src/main/resources/sql/init_c.sql

docker exec -i di-mysql mysql -uroot -pmysql123 --default-character-set=utf8mb4 collegeC \
    < college-c/src/main/resources/sql/init_c_data.sql
```

---

## 运行测试

```bash
# 全部单元测试（~110 个，不需要 Docker）
mvn test -Dsurefire.failIfNoSpecifiedTests=false

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

课程共享：LIST_SHARED_COURSES → Integration 向 B/C 拉取 → formatX.xsl 统一 → 合并 → unifiedToX.xsl 返回
跨院选课：ENROLL → 前缀检测（AC/BC/CC）→ CROSS_ENROLL → APPLY_CHOICE → 目标院写入选课表
跨院退课：WITHDRAW → 同上 → CROSS_WITHDRAW → REVOKE_CHOICE → 目标院删除
全局统计：STATS_GLOBAL → Integration 向三院 STATS_PULL → 聚合 → 返回报表
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
