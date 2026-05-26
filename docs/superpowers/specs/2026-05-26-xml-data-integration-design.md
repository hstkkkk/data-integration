# 基于 XML 的异构数据集成 —— 集成教务系统设计

**项目：** 数据库系统课程作业三
**日期：** 2026-05-26
**目标：** 实现学院 A/B/C 三个异构 DBMS 教务系统的数据集成，基于 XML 技术完成课程共享、跨院选课、全局统计、退课等流程。
**参考课件：** `基于XML数据集成的集成教务系统示例.pdf`（项目根目录）

---

## 1. 项目背景与目标

### 1.1 场景
- 学院 A：SQL Server，教学管理系统
- 学院 B：Oracle，教学管理系统
- 学院 C：MySQL，教学管理系统
- 三院学生互不覆盖，但课程信息有所重叠
- 数据库结构有所差异（表结构、字段名、字段类型、字段数量、字段语义）

### 1.2 需求
1. 各院系教务系统各持 50 学生 / 10 课程 / 每生 5 门选课
2. 通过新增**集成服务器**，实现课程共享：跨院选课成功后将学生与选课信息导入原课程所在院数据库
3. 集成服务器端统计全院学生、课程及选课信息
4. 实现集成环境下的退课流程

### 1.3 硬性要求
- 必须采用 XML 技术
- A/B/C 系统必须有 GUI 与登录环节
- 报告中必须包含数据集成相关流程图

### 1.4 已确定的技术选型
| 项 | 选型 | 备注 |
|---|---|---|
| DBMS | SQL Server (A) / Oracle (B) / MySQL (C) | 全部本地真实部署 |
| 主语言 | Java 17+ | |
| XML 处理 | DOM4J（读写）+ Xerces（验证）+ XSLT/TrAX（转换） | 与课件一致 |
| GUI | Swing | JDK 自带，零依赖 |
| 进程拓扑 | 5 进程：3 院系 Server + 集成 Server + 客户端 | |
| 通信协议 | 原始 Socket + XML 负载（自定义文本帧） | 与课件一致 |
| 数据初始化 | 脚本生成 fixture，GUI 也支持手动增减 | |
| 项目结构 | Maven 多模块 monorepo | |
| 交付物 | 可运行代码 + Markdown 报告 | |

---

## 2. 总体架构

### 2.1 节点拓扑

```
                                SQL Server
                              ┌────────────┐
                              │ DB - 院系A │
                              └─────▲──────┘
                                    │ JDBC
┌────────────┐    Socket     ┌──────┴──────┐
│ Client A   │ ◀────────────▶│  Server  A  │◀─┐
└────────────┘   (本院命令)   └─────────────┘  │
                                               │
                                Oracle         │
                              ┌────────────┐   │ Socket + XML
                              │ DB - 院系B │   │ (院系格式 ↔
                              └─────▲──────┘   │   统一格式)
                                    │ JDBC     │
┌────────────┐    Socket     ┌──────┴──────┐   │     ┌──────────────┐
│ Client B   │ ◀────────────▶│  Server  B  │◀──┼────▶│ Integration  │
└────────────┘   (本院命令)   └─────────────┘   │     │   Server     │
                                                │     │              │
                                MySQL           │     │ XSD + XSL    │
                              ┌────────────┐    │     │ (无业务库)    │
                              │ DB - 院系C │    │     └──────────────┘
                              └─────▲──────┘    │
                                    │ JDBC      │
┌────────────┐    Socket     ┌──────┴──────┐    │
│ Client C   │ ◀────────────▶│  Server  C  │◀───┘
└────────────┘   (本院命令)   └─────────────┘
```

### 2.2 节点职责

| 节点 | 职责 | 不做什么 |
|---|---|---|
| **Client (A/B/C)** | Swing GUI、登录、本院命令发起、显示结果 | 不直接连数据库；不直接联系集成 Server |
| **College Server (A/B/C)** | 本院业务（登录、查询、选课、退课、CRUD）、JDBC 连本院 DB；遇跨院请求委托给集成 Server | 不持有其他院的 schema；不直接联系其他 College Server |
| **Integration Server** | 中间件：接收命令、向各院 Server 拉/推 XML、Schema 验证、XSLT 转换、聚合统计 | 不持久化业务数据（仅缓存与日志）|

### 2.3 通信路径
- **客户端 ↔ 本院 Server**（纵向）：本院登录、查询、本院选课、本院退课
- **院系 Server ↔ 集成 Server**（横向，星型）：所有跨院请求；任意两个院系之间从不直接通信

---

## 3. 代码组织（Maven 多模块）

```
data-integration/
├── pom.xml                       # 父 pom：统一依赖（DOM4J、Xerces、JDBC 驱动）
├── common/                       # 公共：消息协议、XML 工具、统一格式 Schema
│   ├── src/main/java/...
│   │   ├── protocol/             # 命令枚举、消息封装/解析、帧编解码
│   │   ├── xml/                  # DOM4J 包装、Xerces 验证器、XSLT 转换器
│   │   └── model/                # 统一 DTO（仅集成层使用）
│   └── src/main/resources/schema/   # formatStudent.xsd / formatClass.xsd / formatChoice.xsd
├── college-a/                    # 院系 A（SQL Server）
│   ├── src/main/java/...
│   │   ├── server/               # CollegeAServer main、Socket 监听、命令分发
│   │   ├── dao/                  # JDBC DAO（A 表结构）
│   │   └── service/              # 本院业务 + 跨院请求委托
│   └── src/main/resources/
│       ├── schema/{studentA,classA,choiceA}.xsd
│       └── sql/init_a.sql        # 建表 + 50/10/5 fixture
├── college-b/                    # 同上，针对 Oracle
├── college-c/                    # 同上，针对 MySQL
├── integration/                  # 集成服务器
│   ├── src/main/java/...
│   │   ├── server/               # IntegrationServer main
│   │   ├── router/               # 命令路由：决定向哪些院系拉数据
│   │   └── transform/            # XSLT 应用：format*.xsl + *To{A,B,C}.xsl
│   └── src/main/resources/xsl/   # 12 个 .xsl（与课件表 3-16 对齐）
├── client/                       # GUI 客户端（一个 jar 通过 --college 参数切换）
│   └── src/main/java/...
│       ├── ui/                   # Swing：登录、选课、管理面板
│       └── net/                  # 复用 common 协议
├── scripts/
│   ├── start-all.sh              # 启动 5 进程
│   └── seed-data/                # fixture 生成器入口
└── docs/
    ├── superpowers/specs/        # 设计 spec（本文）
    └── report.md                 # 最终交付的报告
```

**关键约束：**
- `common` 只放跨进程共享物（协议、统一格式 schema、转换工具），不放业务代码
- 三个 college 模块业务"互不可见"，体现真正的"异构独立运行"
- 每个学院的 schema/SQL 都放在自己模块的 resources 里，启动时被 College Server 读取
- 客户端是统一 jar，靠启动参数 `--college=A|B|C` 与服务器地址切换
- 启动顺序：3 个 College Server → Integration Server → 客户端

---

## 4. 数据模型

### 4.1 三院本地表结构（严格按课件 P74-P76）

| 实体 | 学院 A (SQL Server) | 学院 B (Oracle) | 学院 C (MySQL) |
|---|---|---|---|
| **账户** | `账户名 varchar(10)` PK / `密码 varchar(6)` / `权限 char(4)` | `账户名 varchar2(12)` PK / `密码 varchar2(12)` / `级别 number(2)` / `客体 varchar2(9)` FK | `acc varchar(12)` PK / `passwd varchar(12)` / `CreateDate timestamp` |
| **学生** | `学号` PK/`姓名`/`性别`/`院系`/`关联账户` FK | `学号` PK/`姓名`/`性别`/`专业`/`密码` | `Sno` PK/`Snm`/`Sex`/`Sde`/`Pwd` |
| **课程** | `课程编号` PK/`课程名称`/`学分`/`授课老师`/`授课地点`/`共享` | `编号` PK/`名称`/`课时`/`学分`/`老师`/`地点`/`共享` | `Cno` PK/`Cnm`/`Ctm`/`Cpt`/`Tec`/`Pla`/`Share` |
| **选课** | `课程编号`+`学生编号` UK / `成绩` | `课程编号` PK/`学号`/`得分` | `Cno` FK/`Sno` FK/`Grd` |

**异构差异（设计上必须保留）：**
- 字段命名：中文 vs 英文 vs 缩写
- 字段数量：A 学生表有"院系"，B 有"专业"，C 用 "Sde"
- 类型差异：A `varchar(8)` vs B `varchar2(5)` vs C `char(4)` 表课程编号
- 账户表完全异构：A 三字段 / B 四字段含级别 / C 三字段含 CreateDate

### 4.2 统一格式 XML（按课件表 3-13/14/15，扩展 origin 字段）

```xml
<!-- 课程 -->
<classes>
  <class>
    <id>...</id> <name>...</name> <time>...</time>
    <score>...</score> <teacher>...</teacher> <location>...</location>
    <share>Y|N</share> <origin>A|B|C</origin>
  </class>
</classes>

<!-- 学生 -->
<students>
  <student>
    <id>...</id> <name>...</name> <sex>...</sex>
    <major>...</major> <origin>A|B|C</origin>
  </student>
</students>

<!-- 选课 -->
<choices>
  <choice>
    <sid>...</sid> <cid>...</cid> <score>...</score>
    <originStudent>A|B|C</originStudent> <originCourse>A|B|C</originCourse>
  </choice>
</choices>
```

> `origin` 字段在课件原版上扩展。用于在统一视图里标识记录来源。统计、跨院选课校验、退课溯源都依赖它。

### 4.3 XSD/XSL 文件清单

| 类型 | 文件数 | 部署位置 |
|---|---|---|
| 院系本地 XSD（学生/课程/选课 各 3 个）| 9 | 各院 College Server |
| 统一格式 XSD（formatStudent/Class/Choice）| 3 | Integration Server |
| 院系格式 → 统一格式 XSL（formatX.xsl）| 3 | Integration Server |
| 统一格式 → 院系格式 XSL（XToA/B/C.xsl × 3 类）| 9 | Integration Server |

总计 **12 XSD + 12 XSL**，与课件完全对齐。

---

## 5. 通信协议

### 5.1 消息帧格式

```
<COMMAND> <REQUEST_ID>\n
Content-Length: <字节数>\n
\n
<XML 负载，UTF-8>
```

- 首行：命令 + 请求 ID（用于异步响应配对）
- `Content-Length` 头解决 socket 粘包/截断
- 空行后是 XML 负载（可为空）

### 5.2 命令字典

**客户端 ↔ 院系 Server：**

| 命令 | 方向 | 负载 | 说明 |
|---|---|---|---|
| `LOGIN` | 客→Srv | 账号/密码 XML | 按本院账户表登录 |
| `LIST_LOCAL_COURSES` | 客→Srv | — | 查本院全部课程 |
| `LIST_SHARED_COURSES` | 客→Srv | — | 查全院共享课程，触发跨院流程 |
| `ENROLL` | 客→Srv | sid/cid XML | 选课，本院/跨院由 Server 判断 |
| `WITHDRAW` | 客→Srv | sid/cid XML | 退课，本院/跨院由 Server 判断 |
| `STATS_GLOBAL` | 客→Srv | — | 全局统计（管理员专用），触发跨院 |
| `OK` / `ERR` | Srv→客 | 结果 XML 或错误信息 | 通用响应 |

**院系 Server ↔ 集成 Server：**

| 命令 | 方向 | 负载 | 说明 |
|---|---|---|---|
| `FETCH_SHARED_COURSES` | 院→集 | — | 集成代为采集所有院共享课 |
| `ASK_COURSE_INFO` | 集→院 | — | 集成向各院索取共享课 XML |
| `SEND_COURSE_INFO` | 院→集 | 院系格式课程 XML | 院系应答 |
| `CROSS_ENROLL` | 院→集 | 院系格式选课+学生 XML | 跨院选课转发 |
| `CROSS_WITHDRAW` | 院→集 | 院系格式选课 XML | 跨院退课转发 |
| `APPLY_CHOICE` | 集→院 | 目标院格式选课+学生 XML | 集成转换后下发 |
| `REVOKE_CHOICE` | 集→院 | 目标院格式选课 XML | 集成下发退课指令 |
| `STATS_PULL` | 集→院 | — | 拉本院学生/课程/选课快照 |
| `STATS_DATA` | 院→集 | 院系格式三类 XML | 应答 |

### 5.3 XML 验证关卡

数据流贯穿 6 道验证（每个边界都验证，体现健壮性）：

1. 院系 Server 出口：本院 XSD 验证（确保发出去的 XML 合法）
2. 集成 Server 入口：本院 XSD 验证（确保收到的格式没坏）
3. 集成 Server 转换：formatX.xsl → 统一格式
4. 集成 Server 中转：用 formatX.xsd 验证统一格式
5. 集成 Server 出口：用 XToY.xsl 转换后，目标院 XSD 验证
6. 目标院 Server 入口：自己再验证一遍

---

## 6. 核心流程

### 6.1 课程共享（A 申请共享所有院的课程）

```
学生/管理员A   ServerA      集成Server         ServerB         ServerC
     │ LIST_SHARED_COURSES      │                │                │
     ├──────────▶│              │                │                │
     │           │ FETCH_SHARED_COURSES          │                │
     │           ├─────────────▶│                │                │
     │           │              │ ASK_COURSE_INFO│                │
     │           │              ├───────────────▶│                │
     │           │              │ ASK_COURSE_INFO│                │
     │           │              ├────────────────────────────────▶│
     │           │              │ SEND_COURSE_INFO (B 格式 XML)   │
     │           │              │◀───────────────┤                │
     │           │              │ SEND_COURSE_INFO (C 格式 XML)   │
     │           │              │◀────────────────────────────────┤
     │           │           [验证 + formatB.xsl + formatC.xsl]   │
     │           │           [合并成统一格式 + classToA.xsl]      │
     │           │ SEND_COURSE_INFO (A 格式 XML)                  │
     │           │◀─────────────┤                │                │
     │           │ [按需写入本地 DB：仅 share='Y' 的课程]         │
     │ OK + 课程列表             │                │                │
     │◀──────────┤              │                │                │
```

**关键点：**
- A 看到的列表 = 本院所有课程 ∪ B/C 的 share='Y' 课程（已转成 A 格式）
- 共享课程**写入** A 本地数据库（`origin` 字段标记来源），符合课件 6.UpdateLocalDB() 的语义
- 同步策略：**懒缓存** —— 触发 `LIST_SHARED_COURSES` 时同步；无后台定时

### 6.2 跨院选课（A 学生选 B 课程）

```
学生A      ServerA            集成Server         ServerB
  │ ENROLL(sid=A001, cid=B12345)  │                  │
  ├─────────▶│                    │                  │
  │          │ [查 cid → 不属本院（来自共享缓存的 origin=B）]
  │          │ CROSS_ENROLL(A 格式: choice + student)│
  │          ├──────────────────▶ │                  │
  │          │            [验证 A 格式 → formatX.xsl]│
  │          │            [统一格式 → studentToB.xsl + choiceToB.xsl]
  │          │            [验证 B 格式]              │
  │          │                    │ APPLY_CHOICE(B 格式)
  │          │                    ├─────────────────▶│
  │          │                    │  [验证 + 检查课程容量 + 学生是否已登记]
  │          │                    │  [若学生未登记：插入学生表（origin=A）]
  │          │                    │  [插入选课表]    │
  │          │                    │ OK 或 ERR        │
  │          │                    │◀─────────────────┤
  │          │ OK / ERR           │                  │
  │          │◀──────────────────┤                  │
  │ OK / ERR │                    │                  │
  │◀─────────┤                    │                  │
  │       [写入 A 本地选课记录（标记 cross=true）] (仅成功时)
```

**幂等性：** A 本地存一份选课副本（`cross=true`）方便学生看自己选的所有课；**B 是真相源**。

### 6.3 全局统计（集成 Server 汇总）

```
管理员    ServerA       集成Server         ServerB        ServerC
   │ STATS_GLOBAL          │                  │              │
   ├──────▶│               │                  │              │
   │       │ FETCH_STATS    │                  │              │
   │       ├──────────────▶│                  │              │
   │       │               │ STATS_PULL ──────▶ ─────────────▶│
   │       │               │ STATS_DATA(B 格式)  ◀─── ◀────────│
   │       │               │ STATS_DATA(C 格式)  ◀──────────────│
   │       │               │ [集成 Server 自查 A]              │
   │       │               │ [全部 → formatX.xsl → 统一格式]   │
   │       │               │ [聚合统计]                        │
   │       │ STATS_RESULT (统一格式 XML)                       │
   │       │◀──────────────┤                  │              │
   │ 报表表格                │                  │              │
   │◀──────┤               │                  │              │
```

**统计维度：** 各院学生数 / 课程数 / 共享课程数；跨院选课总数与占比；课程被选热度排行 Top N。

### 6.4 退课（区分本院/跨院）

```
学生A   ServerA               集成Server          ServerB
  │ WITHDRAW(sid=A001, cid=?)     │                  │
  ├──────▶│                       │                  │
  │       │ [查本地选课记录里 cross 标记]            │
  │   ┌──┴── 本院课程 (cross=false) ──┐              │
  │   │  直接 DELETE FROM 选课       │              │
  │   │  返回 OK                     │              │
  │   └──────────────────────────────┘              │
  │   ┌──┴── 跨院课程 (cross=true, origin=B) ──┐    │
  │   │  CROSS_WITHDRAW (A 格式 choice)         │    │
  │   ├──────────────────────────▶│             │    │
  │   │                  [A→统一→B 转换+验证]   │    │
  │   │                           │ REVOKE_CHOICE (B 格式)
  │   │                           ├──────────────▶│  │
  │   │                           │  DELETE FROM 选课 (B)
  │   │                           │  OK / ERR     │  │
  │   │                           │◀──────────────┤  │
  │   │  OK / ERR                 │               │  │
  │   │◀──────────────────────────┤               │  │
  │   │  仅成功时：删本地选课副本                  │  │
  │   └──────────────────────────────────────────┘  │
  │ OK / ERR                       │                  │
  │◀──────┤                       │                  │
```

**关键设计：本地副本是缓存，不是真相**。跨院退课必须先成功删 B 的，再删 A 的副本，避免出现"A 显示已退但 B 还在"。

---

## 7. 错误处理与跨院一致性

### 7.1 错误分级

| 等级 | 例子 | 策略 |
|---|---|---|
| L1 协议错误 | 消息帧损坏 / Content-Length 不符 | 立即 `ERR PROTOCOL`，断连接，记日志 |
| L2 XML 验证失败 | 收到的 XML 不符合本地 XSD | `ERR XML_SCHEMA` + 错误位置；不写库；失败 XML 存 `logs/rejected/` |
| L3 业务规则错误 | 学生不存在 / 课程已选 | `ERR BUSINESS` + 业务码 |
| L4 跨院故障 | 集成 Server 联系不到目标院 | `ERR UNREACHABLE`，触发回滚 |
| L5 数据库错误 | JDBC 异常、约束冲突 | `ERR DB`，事务回滚，记日志 |

### 7.2 跨院事务一致性

不实现 2PC，而是采用 **"B 为真相源 + 补偿"** 策略：

- 跨院选课：先 B 写成功 → 再写 A 副本；写 A 副本失败 → 发 `REVOKE_CHOICE` 补偿删 B
- 跨院退课：先 B 删成功 → 再删 A 副本；A 副本删失败也无碍（B 是真相，下次同步会自愈）

**报告中需明确说明此权衡。**

### 7.3 降级行为

- B Server 离线时：A 学生只能看本院课程（共享课程缓存里来自 B 的标灰）
- 集成 Server 离线时：客户端只能本院操作；登录、本院选课不受影响
- 数据库离线时：本院 Server 启动失败（fail fast）

### 7.4 日志

- `logs/<server>/access.log`：每条命令一行（命令、reqId、耗时、结果码）
- `logs/<server>/xml/`：XML 收发的明文存档（按 reqId 命名）
- `logs/integration/transform/`：每次 XSLT 转换前后的 XML 对照存档（报告"演示"素材）

---

## 8. 测试策略

| 层 | 工具 | 覆盖 |
|---|---|---|
| XML 单元测试 | JUnit 5 | XSD 验证（合法/非法样例）、XSL 转换（输入→预期输出对照） |
| DAO 层 | JUnit + 真实 DBMS（dev profile） | 每个 DAO 增删改查，验证各 DBMS 方言 |
| 协议层 | JUnit + ServerSocket 模拟 | 帧编解码、粘包/截断处理 |
| 集成测试 | 启动脚本 + JUnit 端到端 | 4 个核心流程的全链路 happy path + 主要异常路径 |
| 手工冒烟 | 启动 5 进程，按演示脚本操作 GUI | 报告截图来源 |

### 8.1 演示脚本（`docs/demo.md`）

1. 启动所有进程，截屏 5 个窗口
2. A 客户端登录，截屏登录界面
3. A 触发课程共享，截屏共享后的课表（标注哪些来自 B/C）
4. A 学生选一门 B 的课，演示集成 Server 日志中转过程
5. 集成 Server 端运行全局统计，截屏报表
6. A 学生退选刚才那门 B 课，演示双向删除
7. 故意发坏 XML，截屏 schema 验证失败提示

—— 这套截屏直接组成报告的"系统演示"章节。

---

## 9. 数据初始化

- `scripts/seed-data/` 里的 Java 程序生成 `init_a.sql` / `init_b.sql` / `init_c.sql`
- 各院 50 学生（中文姓名随机组合）、10 课程（含 3-5 门 share='Y'）、每生 5 选课
- 三院学生学号互不重叠（A: `AS001-AS050`，B: `BS001-BS050`，C: `CS001-CS050`），课程编号同理
- 课程内容设计上有部分相似（"数据库原理"在 A 和 B 都有，但参数不同），便于演示"共享但保留本院版本"

---

## 10. 交付物清单

| 交付物 | 路径 |
|---|---|
| Maven 多模块源码 | 项目根目录所有模块 |
| 各院数据库建库脚本 + fixture | `college-{a,b,c}/src/main/resources/sql/` |
| XML Schema（12 个） | 各院模块 + integration 模块 resources |
| XSL 转换文件（12 个） | `integration/src/main/resources/xsl/` |
| 启动脚本 | `scripts/start-all.sh` |
| 演示脚本 | `docs/demo.md` |
| 设计文档（本文） | `docs/superpowers/specs/2026-05-26-xml-data-integration-design.md` |
| 最终报告 | `docs/report.md` |
| README | 项目根 `README.md`（环境准备、启动、演示入口） |
