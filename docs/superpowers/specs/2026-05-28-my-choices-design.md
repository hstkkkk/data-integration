# 「我的选课」（含跨院）设计 spec

> Date: 2026-05-28
> Status: Approved (设计阶段)
> Owner: I605215

## 1. 目标

让登录任意学院 GUI 的学生能在客户端看到自己**全部**选课，包括跨院选课。本院选课用本院字段命名展示；跨院选课带 `origin` 来源标签。即便部分外院不可达，本院数据必须照常呈现。

## 2. 背景与既有约束

跨院选课只在**目标院**数据库写入选课记录，源院无任何反向引用（参见 `docs/demo.md#7 已知限制`）。例如：AS001 选了 BC003，记录写在 B 院 `选课` 表，A 院本地表里没有。所以「拿全 AS001 的所有选课」必须**问遍三院**，与现有 `STATS_GLOBAL → STATS_PULL` 的模式同构。

三院 `ChoiceDao.findByStudent(studentId)` 已实现，可直接复用。

## 3. 端到端架构

```
[Client GUI]
  ├─ 用户点「我的选课」
  └─ 发: LIST_MY_CHOICES <sno>AS001</sno>
       │
       ▼
[Home College Server (X)]
  ├─ ListMyChoicesHandler:
  │   ① 查本院 ChoiceDao.findByStudent(sno) join CourseDao → 本院字段 XML 块
  │   ② 转发 PULL_MY_CHOICES (sno=AS001, home=X) → integration
       │
       ▼
[Integration Server]
  ├─ PullMyChoicesHandler:
  │   并发 fan-out 到「除 home 外」两院发 ASK_MY_CHOICES sno=AS001
  │   每院返回本院字段命名的「这个 sno 在我这边的选课」
  │   integration 用 formatX-myChoice.xsl 翻成统一 <classes> + sno + grade
  │   过 formatMyChoice.xsd 校验
  │   合并成 <crossEnrolled><classes>...</classes></crossEnrolled>
  │   每院故障收集为 <errors><error college="X">...</error></errors>
       │
       ▼
[Home College Server (X)] 拼接：
  <myChoices sno="AS001" home="A">
    <home>...本院 join 出的 XML...</home>
    <crossEnrolled>...</crossEnrolled>
    <errors>...</errors>
  </myChoices>
       │
       ▼
[Client]
  ├─ 解析 home / crossEnrolled / errors
  ├─ home 直接按本院字段名渲染 JTable (本院块)
  ├─ crossEnrolled 走 unifiedMyChoiceToX.xsl → 本院字段名 → 渲染另一张表 (跨院块, 多列「来自学院」)
  └─ errors 显示在底部状态行（红字）
```

## 4. 协议

### 4.1 新增命令

```java
public enum Command {
  // ... 既有
  LIST_MY_CHOICES,    // client → home college
  PULL_MY_CHOICES,    // home college → integration
  ASK_MY_CHOICES,     // integration → 任一 college
}
```

### 4.2 LIST_MY_CHOICES — 客户端发起

请求 payload：
```xml
<sno>AS001</sno>
```

响应 payload（OK 情况，A 院登录示例）：
```xml
<myChoices sno="AS001" home="A">
  <home>
    <课程集>
      <课程>
        <课程编号>AC001</课程编号><课程名称>Math</课程名称>
        <课时>48</课时><学分>3.0</学分>
        <授课老师>Zhao</授课老师><授课地点>A101</授课地点>
        <学生编号>AS001</学生编号><成绩></成绩>
      </课程>
    </课程集>
  </home>
  <crossEnrolled>
    <classes>
      <class origin="B">
        <id>BC003</id><name>OS</name><time>32</time><score>3</score>
        <teacher>Wang</teacher><location>B201</location>
        <share>Y</share><sno>AS001</sno><grade></grade>
      </class>
    </classes>
  </crossEnrolled>
  <errors>
    <!-- 可空 -->
    <error college="C">unreachable: Connection refused</error>
  </errors>
</myChoices>
```

错误响应：
- `BAD_PAYLOAD` — sno 解析失败 / payload 非法
- `LOCAL_QUERY_FAILED` — 本院 DB 查询异常

### 4.3 PULL_MY_CHOICES — home college → integration

请求 payload：
```xml
<myChoicesReq sno="AS001" home="A"/>
```

响应 payload（OK）：
```xml
<crossEnrolledResult>
  <classes>
    <class origin="B">...</class>
    <class origin="C">...</class>
  </classes>
  <errors>
    <error college="C">...</error>
  </errors>
</crossEnrolledResult>
```

`integration` 即使个别外院失败也返回 OK，错误塞进 `<errors>`。只有 home 字段缺失或 payload 解析失败才返回 ERR `BAD_PAYLOAD`。

### 4.4 ASK_MY_CHOICES — integration → college (任一)

请求 payload：
```xml
<sno>AS001</sno>
```

各院响应（**本院字段命名**）：

A 院：
```xml
<myChoiceSet sno="AS001">
  <课程>
    <课程编号>AC001</课程编号><课程名称>Math</课程名称>
    <课时>48</课时><学分>3.0</学分>
    <授课老师>Zhao</授课老师><授课地点>A101</授课地点>
    <学生编号>AS001</学生编号><成绩></成绩>
  </课程>
</myChoiceSet>
```

B 院：
```xml
<myChoiceSet sno="AS001">
  <课程>
    <编号>BC003</编号><名称>OS</名称>
    <课时>32</课时><学分>3.0</学分>
    <老师>Wang</老师><地点>B201</地点>
    <学号>AS001</学号><得分></得分>
  </课程>
</myChoiceSet>
```

C 院：
```xml
<myChoiceSet sno="AS001">
  <course>
    <Cno>CC005</Cno><Cnm>Net</Cnm>
    <Ctm>32</Ctm><Cpt>2.0</Cpt>
    <Tec>Li</Tec><Pla>C301</Pla>
    <Sno>AS001</Sno><Grd></Grd>
  </course>
</myChoiceSet>
```

错误响应：`LOCAL_QUERY_FAILED`。

## 5. 文件改动清单

### 5.1 新建文件

| 文件 | 类型 | 说明 |
|---|---|---|
| `college-a/src/main/java/college/a/server/handler/AskMyChoicesHandler.java` | Java | 入参 `<sno>`，本地 join 后用本院字段名返回 |
| `college-a/src/main/java/college/a/server/handler/ListMyChoicesHandler.java` | Java | 本院 join + 调用 integration `PULL_MY_CHOICES`，拼装 `<myChoices>` |
| `college-b/.../AskMyChoicesHandler.java` | Java | 同上 B 院 |
| `college-b/.../ListMyChoicesHandler.java` | Java | 同上 B 院 |
| `college-c/.../AskMyChoicesHandler.java` | Java | 同上 C 院 |
| `college-c/.../ListMyChoicesHandler.java` | Java | 同上 C 院 |
| `integration/src/main/java/integration/server/handler/PullMyChoicesHandler.java` | Java | fan-out ASK_MY_CHOICES，formatX-myChoice.xsl 翻译，formatMyChoice.xsd 校验，合并 crossEnrolled + errors |
| `integration/src/main/resources/xsl/formatA-myChoice.xsl` | XSL | A 院 `<myChoiceSet>` → 统一 `<classes>` + sno + grade |
| `integration/src/main/resources/xsl/formatB-myChoice.xsl` | XSL | B 院 同上 |
| `integration/src/main/resources/xsl/formatC-myChoice.xsl` | XSL | C 院 同上 |
| `common/src/main/resources/schema/formatMyChoice.xsd` | XSD | 统一 `<classes>` + 必填 sno + 可空 grade |
| `college-a/src/main/resources/xsl/unifiedMyChoiceToA.xsl` | XSL | 统一 → A 院字段（用于客户端展示跨院块） |
| `college-b/src/main/resources/xsl/unifiedMyChoiceToB.xsl` | XSL | 同上 B |
| `college-c/src/main/resources/xsl/unifiedMyChoiceToC.xsl` | XSL | 同上 C |
| `client/src/main/java/client/ui/MyChoicesDialog.java` | Java | 模态窗口，两张 JTable + 错误 label |

### 5.2 修改文件

| 文件 | 修改 |
|---|---|
| `common/src/main/java/cn/edu/di/protocol/Command.java` | 加 3 个枚举值 |
| `college-a/src/main/java/college/a/server/CollegeAServer.java` | 注册 ListMyChoicesHandler、AskMyChoicesHandler |
| `college-b/.../CollegeBServer.java` | 同上 |
| `college-c/.../CollegeCServer.java` | 同上 |
| `integration/src/main/java/integration/server/IntegrationServer.java` | 注册 PullMyChoicesHandler |
| `client/src/main/java/client/ui/CourseListFrame.java` | 加「我的选课」按钮 → 调起 MyChoicesDialog |

### 5.3 测试新增

| 测试类 | 模块 | 关键 case |
|---|---|---|
| `AskMyChoicesHandlerTest` × 3 | college-a/b/c | mock DAO 返 1 row → 断言本院字段；mock 返空 → 断言空 `<myChoiceSet>`；本院 join 失败 → 断言 ERR LOCAL_QUERY_FAILED |
| `ListMyChoicesHandlerTest` × 3 | college-a/b/c | mock 本院 DAO + mock integration → 断言 home 与 crossEnrolled 都在；integration timeout → home 仍在 + `<error college="*">integration unreachable</error>` |
| `PullMyChoicesHandlerTest` | integration | home=A 只调 B/C 客户端；home=B 只调 A/C；home=C 只调 A/B；某外院返回 ERR → `<error>`；某外院抛 IOException → `<error>` |
| `MyChoiceXslTest` × 3 | integration | formatA-myChoice.xsl 1 输入 → 断言 `<class origin="A"><sno>...</sno><grade></grade>` 字段对 |
| `MyChoiceSchemaTest` | common | formatMyChoice.xsd 接受合法 XML、拒绝缺 sno 的 XML |
| `UnifiedMyChoiceToXXslTest` × 3 | college-a/b/c | unifiedMyChoiceToA.xsl 输入跨院 class → 断言输出 A 院字段 + 保留 origin |

总计 ~15 个新测试用例。客户端 `MyChoicesDialog` 不写 UI 测试（与项目现有 `CourseListFrame` 一致）。

## 6. 错误处理矩阵

| 场景 | 处理位置 | 客户端表现 |
|---|---|---|
| sno 在所有院都没选课 | 各 handler 正常返回空集 | 弹窗显示「该学生无任何选课记录」 |
| 单个外院 socket 拒连/超时 | integration 在 errors 中加条目，其他院正常 | 弹窗主表部分数据 + 底部红字 |
| 单个外院返回 ERR | 同上，detail 取自 ERR payload | 同上 |
| 单个外院 XML 不通过 XSD | 同上，detail 是 XSD errors 字符串 | 同上 |
| home 院本地查询挂 | home `ListMyChoicesHandler` 直接 ERR `LOCAL_QUERY_FAILED` | 整窗显示「错误: LOCAL_QUERY_FAILED」 |
| sno 解析失败 / payload 非法 | home college 返回 ERR `BAD_PAYLOAD` | 整窗显示错误 |
| integration 不可达 | home `ListMyChoicesHandler` 接 IOException → home 块照常返回，crossEnrolled 空 + `<error college="*">integration unreachable</error>` | 弹窗本院数据 + 底部红字 |

**底线**：本院数据能查到就一定呈现；外院尽力而为。

## 7. 不在范围内（YAGNI）

- 历史成绩查询（成绩字段始终空）
- 选课时间排序
- 按学期/学年过滤
- 全屏 Tab 改造（保持模态窗口形态）
- 页面分页（学生选课 5 门左右，无需）
- 客户端缓存（每次点按钮重新拉取）
- 协议层加签名/认证（项目无 auth 机制，沿用既有）

## 8. 决策记录

- **本院字段 vs 统一格式**：本院块直接用本院字段名（学生熟悉的术语），跨院块用统一格式经客户端 XSL 翻成本院字段名。
- **新增 XSL/XSD vs 扩展现有**：新建专用 `formatX-myChoice.xsl`、`formatMyChoice.xsd`、`unifiedMyChoiceToX.xsl`。理由：与「共享课程」流水线职责清晰隔离，不动无关代码，每个新文件 < 30 行。
- **integration 排除 home 院**：避免对同一学生重复查询。`PullMyChoicesHandler` 用 home 字段决定向哪两院 fan-out。
- **错误显式化**：integration 即使部分外院失败也返回 OK，错误塞 `<errors>` 子元素，让客户端清晰看到「这个院走损」。
- **客户端入口**：`CourseListFrame` 加按钮 → 模态 `MyChoicesDialog`，与现有「全局统计」交互模式一致。
- **空成绩字段**：选课表 `成绩 / 得分 / Grd` 列允许 NULL；XML 始终输出该元素但内容可空（即 `<grade></grade>`）。XSD 中将 grade 的 type 定义为 `xs:string` 而非 `xs:decimal`，避免 NULL 转空字符串后无法通过 decimal 校验。
