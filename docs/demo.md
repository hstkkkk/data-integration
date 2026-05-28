# 异构数据集成（XML）跨院教务系统 Demo 脚本

## 0. 前置条件

- macOS 或 Linux，Docker Desktop / Docker Engine 已安装
- JDK 17+（项目以 SapMachine 21 验证）
- Maven 3.9+
- 端口 9001 / 9002 / 9003 / 9100 / 1433 / 1521 / 3306 未被占用

## 1. 启动全部进程

```bash
./scripts/start-all.sh
```

脚本顺序：
1. 启动前校验 4 个 server 端口（9001/9002/9003/9100）空闲，否则立即退出
2. 启动三个 DBMS 容器：`di-sqlserver`（A）、`di-oracle`（B）、`di-mysql`（C）
3. `mvn -DskipTests install` 构建全部模块；构建失败立即退出（不会拿旧 classes 蒙混）
4. 生成各模块运行时 classpath 文件
5. 后台启动 4 个 JVM：Integration Server :9100、College A :9001、College B :9002、College C :9003
6. 探活：每个 server 等 15 秒看真的在监听端口；启动失败则 dump 日志末尾 20 行后退出

各进程日志位于 `logs/` 目录，PID 文件以便后续 stop。

> **注意**：脚本不会自动建表 / 灌种子数据。首次运行需先按 `README.md#数据库初始化` 一节灌三院数据。

## 2. 启动三个客户端

在三个独立终端各跑一条：

```bash
java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) \
    client.Main --college=A --server=127.0.0.1:9001

java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) \
    client.Main --college=B --server=127.0.0.1:9002

java -cp client/target/classes:common/target/classes:$(cat client/target/classpath.txt) \
    client.Main --college=C --server=127.0.0.1:9003
```

## 3. 演示流程（对应作业 4 条要求）

### 要求 1 — 单院教务系统（GUI + 登录）
1. 在 A 客户端登录窗口输入账号 `as001` / 密码 `pwd001`，回车
2. 登录成功后进入课程列表
3. 点击「刷新本院课程」→ 表格显示 10 门 A 院课程（A 院 XML：`课程编号 / 课程名称 / 学分 / 授课老师 / 授课地点`）
4. 选中一行，点击「选课」→ 弹窗默认填入登录账号作为学生编号 → 确认
5. 状态栏显示「选课成功: AS001 / AC001」，说明本院选课链路正常
6. 点击「退课」走相同流程，验证本院退课

A、B、C 三院按上述流程各自演示，证明三个 DBMS（SQL Server / Oracle / MySQL）+ 三套字段命名（中文长名 / 中文短名 / 英文 Cno/Sno）能各自独立工作。

### 要求 2 — 课程共享 + 跨院选课
1. A 客户端点击「刷新共享课程」
   - 客户端 → A Server : `LIST_SHARED_COURSES`
   - A Server → Integration Server : `FETCH_SHARED_COURSES`（payload `<from>A</from>`）
   - Integration Server → B/C Server : `ASK_COURSE_INFO`，分别取得 B/C 原生 XML
   - Integration Server 用 `formatB.xsl / formatC.xsl` 转为统一 `<classes>` 格式 → XSD 校验 → 合并
   - A Server 用 `unifiedToA.xsl` 把统一格式转回 A 院字段 → 客户端表格直接复用本院解析
2. 选中一门 B 院共享课（如 `BC003`），点击「选课」
   - 客户端 → A Server `ENROLL`（payload 用 A 院字段）
   - A 院 `EnrollLocalHandler` 检测到 courseId 不以 `AC` 开头 → 转发 `CROSS_ENROLL`
   - Integration Server `CrossEnrollHandler` 按前缀路由到 B → `APPLY_CHOICE`
   - B 院 `ApplyChoiceHandler` 写入 `选课` 表（学号 = AS001 跨院学生）
3. 状态栏显示「选课成功: AS001 / BC003」

### 要求 3 — 跨院退课
1. 续上：在 A 客户端继续选中 BC003，点击「退课」
   - 客户端 → A Server `WITHDRAW`
   - A Server `WithdrawLocalHandler` 检测前缀 → 转发 `CROSS_WITHDRAW`
   - Integration Server `CrossWithdrawHandler` 路由到 B → `REVOKE_CHOICE`
   - B 院 `RevokeChoiceHandler` 删除该选课记录
2. 状态栏显示「退课成功: AS001 / BC003」

### 要求 4 — 全局统计
1. 任一客户端点击「全局统计」
   - 客户端 → 本院 Server `STATS_GLOBAL`
   - 本院 `StatsForwardHandler` 转发到 Integration Server
   - Integration Server `StatsGlobalHandler` 并发向三院 `STATS_PULL`
   - 各院 `StatsPullHandler` 返回 `<pullData college="X">…</pullData>`
   - Integration Server 聚合 → `<stats><summary/><byCollege/><topCourses/></stats>`
2. 弹窗显示：
   - 全局汇总：学生总数 / 课程总数 / 共享课程总数 / 跨院选课总数
   - 各院明细：A/B/C 各 students / courses / shared / crossEnrollments
   - Top 5 课程（按选课数降序）

### 加分需求 — 我的选课（含跨院聚合）
1. A 客户端点击「我的选课」→ 默认填入登录账号 → 确认
   - 客户端 → A Server `LIST_MY_CHOICES`（payload `<sno>AS001</sno>`）
   - A Server `ListMyChoicesHandler` 本院 join 出 `<home><课程集>...</课程集></home>` 段（A 院字段命名）
   - A Server 转发 `PULL_MY_CHOICES` 给 Integration（payload `<myChoicesReq sno="AS001" home="A"/>`）
   - Integration `PullMyChoicesHandler` 跳过 home=A，并发向 B/C 发 `ASK_MY_CHOICES`
   - B/C 的 `AskMyChoicesHandler` 各自查 `选课 WHERE 学号=AS001` join 课程，回本院字段命名 `<myChoiceSet>`
   - Integration 用 `formatB-myChoice.xsl` / `formatC-myChoice.xsl` 翻成统一 `<classes><class origin="B">...`
   - `formatMyChoice.xsd` 校验后合并；外院故障塞 `<errors><error college="C">...</error>`
   - A Server 收到 `<crossEnrolledResult>` 后用 `unifiedMyChoiceToA.xsl` 把跨院块翻成 A 院字段，整体拼成 `<myChoices sno=... home="A">`
2. 弹窗显示两张表：
   - 上：本院选课（A 院字段：课程编号 / 课程名称 / 学分 / 授课老师 / 授课地点 / 成绩）
   - 下：跨院选课（多列「来源」标记 B 或 C）
   - 底部 status：加载完成行数 或 红字外院故障详情

## 4. 关键 XML 资源（13 个 XSL + 4 个 XSD）

| 文件 | 角色 |
|------|------|
| `integration/src/main/resources/xsl/formatA.xsl` | A 院原生 → 统一（共享课聚合） |
| `integration/src/main/resources/xsl/formatB.xsl` | B 院原生 → 统一（共享课聚合） |
| `integration/src/main/resources/xsl/formatC.xsl` | C 院原生 → 统一（共享课聚合） |
| `integration/src/main/resources/xsl/identity.xsl` | 身份变换（用于回归测试） |
| `integration/src/main/resources/xsl/formatA-myChoice.xsl` | A 院 `<myChoiceSet>` → 统一 `<classes>` + sno + grade |
| `integration/src/main/resources/xsl/formatB-myChoice.xsl` | B 院 `<myChoiceSet>` → 统一（同上） |
| `integration/src/main/resources/xsl/formatC-myChoice.xsl` | C 院 `<myChoiceSet>` → 统一（同上） |
| `college-a/src/main/resources/xsl/unifiedToA.xsl` | 统一 → A 院本地（共享课展示） |
| `college-b/src/main/resources/xsl/unifiedToB.xsl` | 统一 → B 院本地（共享课展示） |
| `college-c/src/main/resources/xsl/unifiedToC.xsl` | 统一 → C 院本地（共享课展示） |
| `college-a/src/main/resources/xsl/unifiedMyChoiceToA.xsl` | 统一 → A 院本地（跨院选课展示，含来源/学号/成绩） |
| `college-b/src/main/resources/xsl/unifiedMyChoiceToB.xsl` | 统一 → B 院本地（同上） |
| `college-c/src/main/resources/xsl/unifiedMyChoiceToC.xsl` | 统一 → C 院本地（同上） |
| `common/src/main/resources/schema/formatClass.xsd` | 统一 `<classes>` 课程格式 XSD 校验（共享课用） |
| `common/src/main/resources/schema/formatStudent.xsd` | 统一学生格式 XSD 校验 |
| `common/src/main/resources/schema/formatChoice.xsd` | 统一选课格式 XSD 校验 |
| `common/src/main/resources/schema/formatMyChoice.xsd` | 统一 `<classes>` + sno + grade 格式 XSD 校验（我的选课用） |

## 5. 关闭

```bash
./scripts/stop-all.sh
```

杀掉 4 个 JVM 进程（按 `logs/*.pid`）并停掉 3 个 DBMS 容器。

## 6. 冒烟结果（最近一次 mvn 执行，2026-05-28）

构建：

```text
[INFO] BUILD SUCCESS
```

测试（按模块汇总，共 146 个测试 0 失败 0 错误）：

```text
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0    (common)
[INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0    (college-a)
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0    (college-b)
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0    (college-c)
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0    (integration)
[INFO] Tests run:  3, Failures: 0, Errors: 0, Skipped: 0    (client)
[INFO] Tests run:  9, Failures: 0, Errors: 0, Skipped: 0    (seed-data)
[INFO] BUILD SUCCESS
```

## 7. 已知限制

- 跨院选课只在目标院数据库写入选课记录；不在源院记录"该学生选了外院某门课"的反向引用。「我的选课」需求通过 fan-out 三院聚合解决了这条查询路径的可达性
- 三院 `选课` 表都额外有 `来源` / `Org` 列保留请求方学院信息；`选课` 表对学生表**没有** FK（跨院学生不在本院学生表，FK 会破坏跨院流）
- 没有事务/2PC：Integration 转发到目标院 `APPLY_CHOICE` 失败时返回客户端，但不会回滚源端任何状态（源端本来就只是转发，无状态可回滚，因此一致性自然保持）
- Integration 不可达时，本院操作仍可独立完成；跨院请求和「我的选课」的跨院块降级（前者 ERR、后者本院块照样返回 + `<errors>` 标记）
