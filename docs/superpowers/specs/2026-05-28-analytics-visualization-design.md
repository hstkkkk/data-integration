# 集成分析及可视化 — 设计 Spec

**日期：** 2026-05-28  
**目标：** 在已有三院异构数据集成系统之上，新建 `analytics` 模块，提供可视化图表、PDF 报告导出和实时监控面板。

---

## 1. 架构

```
                                  analytics 模块
                               ┌──────────────────────┐
                               │ chart/               │ ← JFreeChart 图表工厂
                               │ pdf/                 │ ← Apache FOP + XSL-FO 模板
                               │ monitor/             │ ← 实时监控数据模型 + 采集
                               │ ui/                  │ ← 可复用 Swing Panel
                               └──────────┬───────────┘
                                          │ depends
                            ┌─────────────┴─────────────┐
                            │          client             │
                            │  + DashboardDialog          │
                            └─────────────────────────────┘
                                          │ TCP
                            ┌─────────────┴─────────────┐
                            │     Integration Server      │
                            │  + MonitorHandler           │
                            │  + AnalyticsHandler (增强)   │
                            └───────┬─────────┬─────────┘
                                    │         │
                        HEARTBEAT ──┘         └── HEARTBEAT
                                    ▼         ▼
                              College A/B/C
                              + HeartbeatHandler
```

**关键约束：**
- `analytics` 模块只依赖 `common`（协议层），不依赖 `college-a/b/c`
- 图表和监控数据经 Integration Server 汇总后返回 Client
- Client 5 秒轮询一次 `MONITOR_STATUS`，非推送模式
- `analytics` 是 client 的依赖，运行时 classpath 共享

---

## 2. 新增 Command

| 命令 | 枚举值 | 方向 | 用途 |
|------|--------|------|------|
| `MONITOR_STATUS` | 新增 | Client→Integration | 一次拉取四台 Server 健康状态 |
| `HEARTBEAT` | 新增 | Integration→College | 向单院探活，返回健康快照 |
| `ANALYTICS_EXPORT` | 新增 | Client→Integration | 触发完整分析报告生成（XML），供 PDF 渲染 |

> `HEARTBEAT` 复用 PING 语义的扩展：PING 返回 `<pong>`，`HEARTBEAT` 返回 `<heartbeat college="A" uptime="..." requestCount="..." errorCount="..."/>`。

---

## 3. 核心组件

### 3.1 图表（`analytics/chart/`）

依赖 JFreeChart (`org.jfree:jfreechart:1.5.5`)。

| 类 | 职责 |
|----|------|
| `ChartFactory` | 静态工厂：`createCollegeBarChart(StatsData)`、`createEnrollmentPieChart(StatsData)`、`createTopCoursesChart(StatsData)` |
| `StatsData` | 不可变数据类，从 `ANALYTICS_EXPORT` 响应 XML 反序列化 |

图表类型：
- **柱状图** (`BarChart`)：三院学生数/课程数/共享课程数/跨院选课数横向对比，4 组柱子
- **饼图** (`PieChart`)：三院选课总量占比分布（A/B/C 各一块）
- **水平条形图** (`HorizontalBarChart`)：课程选课热度 Top 10，按选课人数降序，颜色区分本院/跨院

### 3.2 PDF 导出（`analytics/pdf/`）

依赖 Apache FOP (`org.apache.xmlgraphics:fop:2.9`)。

流程：`StatsData` → 序列化为统一 XML → XSL-FO 模板 → Apache FOP → PDF 字节流 → 写入文件。

| 文件 | 职责 |
|------|------|
| `PdfReportGenerator.java` | 接收 `StatsData` + 输出路径，生成 PDF |
| `src/main/resources/xsl-fo/report.xsl` | XSL-FO 模板：页眉（标题+时间戳）、三院对比表格、Top 10 课程列表、页脚（页码） |

PDF 报告内容结构：
1. 封面：标题"集成教务系统分析报告" + 生成时间
2. 系统概览表：总学生数/总课程数/共享课程数/跨院选课数
3. 各院明细表：A/B/C 行 × 学生数/课程数/共享/跨院选课列
4. 课程热度排行：Top 10 表格
5. 页脚：页码 / 总页数

### 3.3 实时监控（`analytics/monitor/`）

| 类 | 职责 |
|----|------|
| `ServerStatus` | record: `String name, boolean online, long latencyMs, int requestCount, int errorCount, long uptimeSeconds` |
| `MonitorSnapshot` | `List<ServerStatus>` 的包装，加时间戳 |
| `MonitorPoller` | Swing `Timer` 驱动的轮询器，5 秒间隔调用 `MONITOR_STATUS` |
| `MonitorPanel` | Swing `JPanel`：4 行指示灯 + 延迟柱 + 请求量，绿色/红色圆点，每行带文字标注 |

Integration Server `MonitorHandler` 逻辑：
1. 并发向三院 + 自查发 HEARTBEAT
2. 每院超时 3 秒视为离线
3. 汇总为 `<monitor timestamp="...">` XML 返回

### 3.4 Client UI（`analytics/ui/`）

| 类 | 职责 |
|----|------|
| `DashboardPanel` | 组合面板：上方 2×2 图表网格，下方监控条 |
| `DashboardDialog` | JDialog 弹窗，标题"集成分析中心"，含"刷新图表""导出 PDF""关闭"三个按钮 |

入口：主界面 `CourseListFrame` 增加"分析中心"按钮 → 打开 `DashboardDialog`。

---

## 4. 数据流

### 4.1 图表数据流

```
Client                        Integration                    College A/B/C
  │ ANALYTICS_EXPORT              │                               │
  ├─────────────────────────────▶│                               │
  │                               │ STATS_PULL (并发)              │
  │                               ├──────────────────────────────▶│
  │                               │◀──────────────────────────────┤
  │                               │ 汇总 + 增强统计                │
  │◀─────────────────────────────┤                               │
  │ JFreeChart 渲染               │                               │
  │ [可选] PDF 导出               │                               │
```

### 4.2 监控数据流

```
Client [Timer 5s]              Integration                    College A/B/C
  │ MONITOR_STATUS                │                               │
  ├─────────────────────────────▶│                               │
  │                               │ HEARTBEAT (并发, timeout 3s)   │
  │                               ├──────────────────────────────▶│
  │                               │◀──────────────────────────────┤
  │                               │ 汇总 MonitorSnapshot           │
  │◀─────────────────────────────┤                               │
  │ MonitorPanel 更新             │                               │
```

---

## 5. Maven 依赖

```xml
<!-- analytics/pom.xml -->
<dependencies>
    <dependency><groupId>cn.edu.di</groupId><artifactId>common</artifactId></dependency>
    <dependency><groupId>org.jfree</groupId><artifactId>jfreechart</artifactId><version>1.5.5</version></dependency>
    <dependency><groupId>org.apache.xmlgraphics</groupId><artifactId>fop</artifactId><version>2.9</version></dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId></dependency>
    <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId></dependency>
</dependencies>
```

父 `pom.xml` 的 `<modules>` 追加 `<module>analytics</module>`，`<dependencyManagement>` 加入 jfreechart 和 fop 版本管理。

---

## 6. 测试策略

| 层 | 方式 | 覆盖 |
|----|------|------|
| `ChartFactory` | JUnit + 断言图表数据集 | 各图表类型至少一个测试（验证 category/value 正确） |
| `PdfReportGenerator` | JUnit + 临时文件 | 生成 PDF 字节数 > 0，含预期文本 |
| `MonitorPoller` | Mock CollegeClient | 模拟在线/离线/超时三种状态，验证 `MonitorSnapshot` 正确 |
| `StatsData` | JUnit | XML 反序列化正确性 |
| 集成 | 启动全系统，手工点"分析中心"按钮 | GUI 冒烟 |

---

## 7. 验收标准

- [ ] `mvn test` 全部通过（含 analytics 模块新测试）
- [ ] "分析中心"按钮可打开 Dashboard，显示至少 3 种图表
- [ ] 监控面板 5 秒刷新，四台 Server 在线状态正确显示
- [ ] 点击"导出 PDF"生成有效 PDF 文件，内容包含三院汇总表格
- [ ] `./scripts/start-all.sh` 包含 analytics 模块的 classpath
- [ ] PDF 中的中文字符不乱码（FOP 注册 Noto CJK 字体）

---

## 8. XSL-FO 模板（草案）

`analytics/src/main/resources/xsl-fo/report.xsl`：

```xml
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:fo="http://www.w3.org/1999/XSL/Format">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

  <xsl:template match="analyticsReport">
    <fo:root>
      <fo:layout-master-set>
        <fo:simple-page-master master-name="A4" page-width="210mm" page-height="297mm">
          <fo:region-body margin="20mm"/>
          <fo:region-before extent="10mm"/>
          <fo:region-after extent="10mm"/>
        </fo:simple-page-master>
      </fo:layout-master-set>

      <fo:page-sequence master-reference="A4">
        <fo:flow flow-name="xsl-region-body">
          <!-- Title -->
          <fo:block font-size="18pt" font-weight="bold" text-align="center"
                    space-after="10mm">集成教务系统分析报告</fo:block>
          <fo:block font-size="10pt" text-align="center"
                    space-after="15mm">生成时间: <xsl:value-of select="@timestamp"/></fo:block>

          <!-- Summary table -->
          <fo:block font-size="14pt" font-weight="bold" space-after="5mm">系统概览</fo:block>
          <fo:table table-layout="fixed" width="100%">
            <fo:table-body>
              <xsl:for-each select="summary/*">
                <fo:table-row>
                  <fo:table-cell><fo:block><xsl:value-of select="local-name()"/></fo:block></fo:table-cell>
                  <fo:table-cell><fo:block><xsl:value-of select="."/></fo:block></fo:table-cell>
                </fo:table-row>
              </xsl:for-each>
            </fo:table-body>
          </fo:table>

          <!-- Per-college detail table (same pattern) -->
          <!-- Top courses table (same pattern) -->
        </fo:flow>
      </fo:page-sequence>
    </fo:root>
  </xsl:template>
</xsl:stylesheet>
```

---

## 9. 文件清单

| 文件 | 类型 | 职责 |
|------|------|------|
| `analytics/pom.xml` | Create | Maven 模块定义 + 依赖 |
| `analytics/src/main/java/analytics/chart/ChartFactory.java` | Create | JFreeChart 工厂 |
| `analytics/src/main/java/analytics/model/StatsData.java` | Create | 统计 DTO + XML 反序列化 |
| `analytics/src/main/java/analytics/model/ServerStatus.java` | Create | 服务器健康状态 DTO |
| `analytics/src/main/java/analytics/model/MonitorSnapshot.java` | Create | 监控快照 |
| `analytics/src/main/java/analytics/monitor/MonitorPoller.java` | Create | 定时轮询控制器 |
| `analytics/src/main/java/analytics/pdf/PdfReportGenerator.java` | Create | FOP PDF 生成 |
| `analytics/src/main/java/analytics/ui/DashboardPanel.java` | Create | 图表+监控组合面板 |
| `analytics/src/main/java/analytics/ui/DashboardDialog.java` | Create | 分析中心弹窗 |
| `analytics/src/main/java/analytics/ui/MonitorPanel.java` | Create | 监控指示灯面板 |
| `analytics/src/main/resources/xsl-fo/report.xsl` | Create | XSL-FO PDF 模板 |
| `integration/src/main/java/integration/server/handler/MonitorHandler.java` | Create | 监控汇总 handler |
| `integration/src/main/java/integration/server/handler/AnalyticsHandler.java` | Create | 增强分析 handler |
| `college-a/src/main/java/college/a/server/handler/HeartbeatHandler.java` | Create | 心跳响应（B/C 同步） |
| `client/src/main/java/client/ui/CourseListFrame.java` | Modify | + 分析中心按钮 |
| `pom.xml` | Modify | + analytics 模块 + 依赖管理 |
| `scripts/start-all.sh` | Modify | + analytics classpath |
