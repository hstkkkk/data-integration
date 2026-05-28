# Plan 4: 集成分析及可视化模块

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `analytics` 模块，实现 JFreeChart 图表、Apache FOP PDF 导出和实时监控面板，通过"分析中心"按钮在客户端打开。

**Architecture:** analytics 模块只依赖 common，提供可复用的图表/PDF/监控组件。Integration Server 新增 MonitorHandler + AnalyticsHandler，College Server 新增 HeartbeatHandler。Client 通过 DashboardDialog 集成所有可视化组件。

**Tech Stack:** Java 17, JFreeChart 1.5.5, Apache FOP 2.9, Swing。复用已有 Common 协议层。

**前置条件:** Plan 1-3 全部完成；三院 Server 和 Integration Server 可正常启动。

**参考:** 设计 spec `docs/superpowers/specs/2026-05-28-analytics-visualization-design.md`

---

## Task 1: Maven 模块骨架 + 依赖

**Files:**
- Create: `analytics/pom.xml`
- Modify: `pom.xml`（父 pom）

- [ ] **Step 1: 写 analytics/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>cn.edu.di</groupId><artifactId>data-integration</artifactId><version>1.0.0-SNAPSHOT</version></parent>
  <artifactId>analytics</artifactId>
  <dependencies>
    <dependency><groupId>cn.edu.di</groupId><artifactId>common</artifactId><version>1.0.0-SNAPSHOT</version></dependency>
    <dependency><groupId>org.jfree</groupId><artifactId>jfreechart</artifactId><version>1.5.5</version></dependency>
    <dependency><groupId>org.apache.xmlgraphics</groupId><artifactId>fop</artifactId><version>2.9</version></dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId></dependency>
    <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId></dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: 修改父 pom.xml**

在 `<modules>` 中追加 `<module>analytics</module>`。

在 `<dependencyManagement>` 中追加：

```xml
<dependency><groupId>org.jfree</groupId><artifactId>jfreechart</artifactId><version>1.5.5</version></dependency>
<dependency><groupId>org.apache.xmlgraphics</groupId><artifactId>fop</artifactId><version>2.9</version></dependency>
```

- [ ] **Step 3: 验证编译**

```bash
mvn -q -DskipTests compile
```

预期 BUILD SUCCESS。

- [ ] **Step 4: commit**

```bash
git add analytics/pom.xml pom.xml
git commit -m "build(analytics): scaffold maven module with jfreechart and fop"
```

---

## Task 2: 数据模型 — StatsData + ServerStatus + MonitorSnapshot

**Files:**
- Create: `analytics/src/main/java/analytics/model/StatsData.java`
- Create: `analytics/src/main/java/analytics/model/ServerStatus.java`
- Create: `analytics/src/main/java/analytics/model/MonitorSnapshot.java`
- Test: `analytics/src/test/java/analytics/model/StatsDataTest.java`
- Test: `analytics/src/test/java/analytics/model/MonitorSnapshotTest.java`

三个 record，StatsData 需要从 XML 反序列化。

- [ ] **Step 1: 写失败测试**

```java
// analytics/src/test/java/analytics/model/StatsDataTest.java
package analytics.model;

import cn.edu.di.xml.XmlIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatsDataTest {
  @Test void parses_analytics_xml() {
    String xml = """
        <analyticsReport timestamp="2026-05-28T10:00:00">
          <summary>
            <totalStudents>150</totalStudents>
            <totalCourses>30</totalCourses>
            <totalShared>12</totalShared>
            <totalCross>45</totalCross>
          </summary>
          <colleges>
            <college code="A"><students>50</students><courses>10</courses><shared>4</shared><cross>15</cross></college>
            <college code="B"><students>50</students><courses>10</courses><shared>4</shared><cross>18</cross></college>
            <college code="C"><students>50</students><courses>10</courses><shared>4</shared><cross>12</cross></college>
          </colleges>
          <topCourses>
            <course id="BC001" name="数据库原理" enrollments="28" origin="B"/>
            <course id="AC001" name="数据库原理" enrollments="26" origin="A"/>
          </topCourses>
        </analyticsReport>""";
    var d = StatsData.fromXml(XmlIO.parse(xml));
    assertEquals(150, d.summary().totalStudents());
    assertEquals(3, d.colleges().size());
    assertEquals("B", d.colleges().get(1).code());
    assertEquals(2, d.topCourses().size());
  }
}
```

Run: `mvn -pl analytics -am test -Dtest=StatsDataTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (StatsData not found).

- [ ] **Step 2: 实现三个 model 类**

```java
// analytics/src/main/java/analytics/model/StatsData.java
package analytics.model;

import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.Element;
import java.util.ArrayList;
import java.util.List;

public record StatsData(Summary summary, List<CollegeStat> colleges, List<CourseEntry> topCourses) {

  public record Summary(int totalStudents, int totalCourses, int totalShared, int totalCross) {}
  public record CollegeStat(String code, int students, int courses, int shared, int cross) {}
  public record CourseEntry(String id, String name, int enrollments, String origin) {}

  public static StatsData fromXml(Document doc) {
    Element root = doc.getRootElement();
    Element s = root.element("summary");
    Summary summary = new Summary(
        Integer.parseInt(s.elementText("totalStudents")),
        Integer.parseInt(s.elementText("totalCourses")),
        Integer.parseInt(s.elementText("totalShared")),
        Integer.parseInt(s.elementText("totalCross")));

    List<CollegeStat> colleges = new ArrayList<>();
    for (Object o : root.element("colleges").elements("college")) {
      Element e = (Element) o;
      colleges.add(new CollegeStat(e.attributeValue("code"),
          Integer.parseInt(e.elementText("students")),
          Integer.parseInt(e.elementText("courses")),
          Integer.parseInt(e.elementText("shared")),
          Integer.parseInt(e.elementText("cross"))));
    }

    List<CourseEntry> top = new ArrayList<>();
    Element tc = root.element("topCourses");
    if (tc != null) {
      for (Object o : tc.elements("course")) {
        Element e = (Element) o;
        top.add(new CourseEntry(e.attributeValue("id"), e.attributeValue("name"),
            Integer.parseInt(e.attributeValue("enrollments")), e.attributeValue("origin")));
      }
    }
    return new StatsData(summary, colleges, top);
  }
}
```

```java
// analytics/src/main/java/analytics/model/ServerStatus.java
package analytics.model;

public record ServerStatus(String name, boolean online, long latencyMs,
                           int requestCount, int errorCount, long uptimeSeconds) {}
```

```java
// analytics/src/main/java/analytics/model/MonitorSnapshot.java
package analytics.model;

import java.time.Instant;
import java.util.List;

public record MonitorSnapshot(Instant timestamp, List<ServerStatus> servers) {
  public MonitorSnapshot(List<ServerStatus> servers) {
    this(Instant.now(), List.copyOf(servers));
  }
}
```

- [ ] **Step 3: PASS + commit**

```bash
mvn -pl analytics -am test -Dtest=StatsDataTest -Dsurefire.failIfNoSpecifiedTests=false
git add analytics/src/
git commit -m "feat(analytics): data models for stats, server status, and monitoring"
```

---

## Task 3: JFreeChart 图表工厂

**Files:**
- Create: `analytics/src/main/java/analytics/chart/ChartFactory.java`
- Test: `analytics/src/test/java/analytics/chart/ChartFactoryTest.java`

工厂类生成三种 JFreeChart：

1. `createComparisonBarChart(StatsData)` — 三院学生数/课程数/共享数/跨院数 4 组柱状图
2. `createEnrollmentPieChart(StatsData)` — 三院选课占比饼图
3. `createTopCoursesChart(StatsData)` — 课程选课热度 Top 10 水平条形图

- [ ] **Step 1: 写测试**

```java
// analytics/src/test/java/analytics/chart/ChartFactoryTest.java
package analytics.chart;

import analytics.model.StatsData;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ChartFactoryTest {
  private final StatsData.Summary summary = new StatsData.Summary(150, 30, 12, 45);
  private final StatsData.CollegeStat a = new StatsData.CollegeStat("A", 50, 10, 4, 15);
  private final StatsData.CollegeStat b = new StatsData.CollegeStat("B", 50, 10, 4, 18);
  private final StatsData.CollegeStat c = new StatsData.CollegeStat("C", 50, 10, 4, 12);
  private final StatsData.CourseEntry top1 = new StatsData.CourseEntry("BC001", "数据库原理", 28, "B");
  private final StatsData stats = new StatsData(summary, java.util.List.of(a, b, c), java.util.List.of(top1));

  @Test void comparisonBarChart_notNull() {
    assertNotNull(ChartFactory.createComparisonBarChart(stats));
  }

  @Test void enrollmentPieChart_notNull() {
    assertNotNull(ChartFactory.createEnrollmentPieChart(stats));
  }

  @Test void topCoursesChart_notNull() {
    assertNotNull(ChartFactory.createTopCoursesChart(stats));
  }

  @Test void emptyStats_producesChartsWithoutException() {
    var empty = new StatsData(new StatsData.Summary(0, 0, 0, 0),
        Collections.emptyList(), Collections.emptyList());
    assertNotNull(ChartFactory.createComparisonBarChart(empty));
    assertNotNull(ChartFactory.createEnrollmentPieChart(empty));
  }
}
```

Run: `mvn -pl analytics -am test -Dtest=ChartFactoryTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL.

- [ ] **Step 2: 实现 ChartFactory**

```java
// analytics/src/main/java/analytics/chart/ChartFactory.java
package analytics.chart;

import analytics.model.StatsData;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import java.awt.*;

public final class ChartFactory {

  private ChartFactory() {}

  public static JFreeChart createComparisonBarChart(StatsData data) {
    var dataset = new DefaultCategoryDataset();
    for (var c : data.colleges()) {
      dataset.addValue(c.students(), "学生数", c.code());
      dataset.addValue(c.courses(), "课程数", c.code());
      dataset.addValue(c.shared(), "共享课程", c.code());
      dataset.addValue(c.cross(), "跨院选课", c.code());
    }
    return org.jfree.chart.ChartFactory.createBarChart(
        "三院数据对比", "学院", "数量", dataset,
        PlotOrientation.VERTICAL, true, true, false);
  }

  public static JFreeChart createEnrollmentPieChart(StatsData data) {
    var dataset = new DefaultPieDataset<String>();
    for (var c : data.colleges()) {
      dataset.setValue("学院 " + c.code(), c.students());
    }
    return org.jfree.chart.ChartFactory.createPieChart(
        "三院学生数占比", dataset, true, true, false);
  }

  public static JFreeChart createTopCoursesChart(StatsData data) {
    var dataset = new DefaultCategoryDataset();
    for (var e : data.topCourses()) {
      dataset.addValue(e.enrollments(), "选课人数", e.name() + " (" + e.origin() + ")");
    }
    return org.jfree.chart.ChartFactory.createBarChart(
        "课程热度 Top " + data.topCourses().size(), "课程", "选课人数", dataset,
        PlotOrientation.HORIZONTAL, false, true, false);
  }
}
```

- [ ] **Step 3: PASS + commit**

```bash
mvn -pl analytics -am test -Dtest=ChartFactoryTest -Dsurefire.failIfNoSpecifiedTests=false
git add analytics/src/
git commit -m "feat(analytics): jfreechart factory for bar/pie/top-courses charts"
```

---

## Task 4: PDF 报告生成器（Apache FOP）

**Files:**
- Create: `analytics/src/main/java/analytics/pdf/PdfReportGenerator.java`
- Create: `analytics/src/main/resources/xsl-fo/report.xsl`
- Test: `analytics/src/test/java/analytics/pdf/PdfReportGeneratorTest.java`

流程：`StatsData.toXml()` → XSL-FO 模板 → Apache FOP → `byte[]` → 写文件。

- [ ] **Step 1: 先给 StatsData 加 toXml() 方法**

修改 Task 2 的 StatsData，加：

```java
public String toXml() {
  StringBuilder sb = new StringBuilder();
  sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
  sb.append("<analyticsReport timestamp=\"").append(java.time.Instant.now()).append("\">");

  sb.append("<summary>");
  sb.append("<totalStudents>").append(summary.totalStudents()).append("</totalStudents>");
  sb.append("<totalCourses>").append(summary.totalCourses()).append("</totalCourses>");
  sb.append("<totalShared>").append(summary.totalShared()).append("</totalShared>");
  sb.append("<totalCross>").append(summary.totalCross()).append("</totalCross>");
  sb.append("</summary>");

  sb.append("<colleges>");
  for (var c : colleges) {
    sb.append("<college code=\"").append(c.code()).append("\">");
    sb.append("<students>").append(c.students()).append("</students>");
    sb.append("<courses>").append(c.courses()).append("</courses>");
    sb.append("<shared>").append(c.shared()).append("</shared>");
    sb.append("<cross>").append(c.cross()).append("</cross>");
    sb.append("</college>");
  }
  sb.append("</colleges>");

  sb.append("<topCourses>");
  for (var c : topCourses) {
    sb.append("<course id=\"").append(c.id()).append("\" name=\"").append(esc(c.name()))
      .append("\" enrollments=\"").append(c.enrollments()).append("\" origin=\"").append(c.origin()).append("\"/>");
  }
  sb.append("</topCourses>");
  sb.append("</analyticsReport>");
  return sb.toString();
}

private static String esc(String s) {
  return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
}
```

- [ ] **Step 2: 写 report.xsl（XSL-FO 模板）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:fo="http://www.w3.org/1999/XSL/Format">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

  <xsl:template match="analyticsReport">
    <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
      <fo:layout-master-set>
        <fo:simple-page-master master-name="A4" page-width="210mm" page-height="297mm"
            margin-top="20mm" margin-bottom="20mm" margin-left="20mm" margin-right="20mm"/>
      </fo:layout-master-set>

      <fo:page-sequence master-reference="A4">
        <fo:flow flow-name="xsl-region-body">
          <fo:block font-size="18pt" font-weight="bold" text-align="center"
              space-after="10mm">集成教务系统分析报告</fo:block>
          <fo:block font-size="10pt" text-align="center"
              space-after="15mm">生成时间：<xsl:value-of select="@timestamp"/></fo:block>

          <!-- Summary -->
          <fo:block font-size="14pt" font-weight="bold" space-after="5mm">系统概览</fo:block>
          <fo:table table-layout="fixed" width="100%" border="1pt solid black">
            <fo:table-body>
              <fo:table-row>
                <fo:table-cell padding="3pt"><fo:block>总学生数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="summary/totalStudents"/></fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>总课程数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="summary/totalCourses"/></fo:block></fo:table-cell>
              </fo:table-row>
              <fo:table-row>
                <fo:table-cell padding="3pt"><fo:block>共享课程</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="summary/totalShared"/></fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>跨院选课</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="summary/totalCross"/></fo:block></fo:table-cell>
              </fo:table-row>
            </fo:table-body>
          </fo:table>

          <!-- College detail -->
          <fo:block font-size="14pt" font-weight="bold" space-before="15mm" space-after="5mm">各院明细</fo:block>
          <fo:table table-layout="fixed" width="100%" border="1pt solid black">
            <fo:table-header>
              <fo:table-row font-weight="bold">
                <fo:table-cell padding="3pt"><fo:block>学院</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>学生数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>课程数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>共享课程</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>跨院选课</fo:block></fo:table-cell>
              </fo:table-row>
            </fo:table-header>
            <fo:table-body>
              <xsl:for-each select="colleges/college">
                <fo:table-row>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="@code"/></fo:block></fo:table-cell>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="students"/></fo:block></fo:table-cell>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="courses"/></fo:block></fo:table-cell>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="shared"/></fo:block></fo:table-cell>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="cross"/></fo:block></fo:table-cell>
                </fo:table-row>
              </xsl:for-each>
            </fo:table-body>
          </fo:table>

          <!-- Top Courses -->
          <fo:block font-size="14pt" font-weight="bold" space-before="15mm" space-after="5mm">课程热度 Top 10</fo:block>
          <fo:table table-layout="fixed" width="100%" border="1pt solid black">
            <fo:table-header>
              <fo:table-row font-weight="bold">
                <fo:table-cell padding="3pt"><fo:block>排名</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>课程名</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>选课人数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>来源</fo:block></fo:table-cell>
              </fo:table-row>
            </fo:table-header>
            <fo:table-body>
              <xsl:for-each select="topCourses/course">
                <fo:table-row>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="position()"/></fo:block></fo:table-cell>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="@name"/></fo:block></fo:table-cell>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="@enrollments"/></fo:block></fo:table-cell>
                  <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="@origin"/></fo:block></fo:table-cell>
                </fo:table-row>
              </xsl:for-each>
            </fo:table-body>
          </fo:table>
        </fo:flow>
      </fo:page-sequence>
    </fo:root>
  </xsl:template>
</xsl:stylesheet>
```

- [ ] **Step 3: 实现 PdfReportGenerator**

```java
// analytics/src/main/java/analytics/pdf/PdfReportGenerator.java
package analytics.pdf;

import analytics.model.StatsData;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class PdfReportGenerator {

  private static final byte[] XSL_FO_TEMPLATE;

  static {
    try (var in = PdfReportGenerator.class.getResourceAsStream("/xsl-fo/report.xsl")) {
      XSL_FO_TEMPLATE = in.readAllBytes();
    } catch (IOException e) { throw new RuntimeException("load report.xsl failed", e); }
  }

  private PdfReportGenerator() {}

  public static byte[] generate(StatsData data) throws Exception {
    String xml = data.toXml();
    var xslSource = new StreamSource(new ByteArrayInputStream(XSL_FO_TEMPLATE));
    var xmlSource = new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    var fopFactory = org.apache.fop.apps.FopFactory.newInstance(new File(".").toURI());
    var out = new ByteArrayOutputStream();
    var fop = fopFactory.newFop(org.apache.xmlgraphics.util.MimeConstants.MIME_PDF,
        fopFactory.newFOUserAgent(), out);

    var transformer = TransformerFactory.newInstance().newTransformer(xslSource);
    transformer.transform(xmlSource, new javax.xml.transform.sax.SAXResult(fop.getDefaultHandler()));
    return out.toByteArray();
  }

  public static void saveToFile(StatsData data, File file) throws Exception {
    java.nio.file.Files.write(file.toPath(), generate(data));
  }
}
```

- [ ] **Step 4: 写测试**

```java
// analytics/src/test/java/analytics/pdf/PdfReportGeneratorTest.java
package analytics.pdf;

import analytics.model.StatsData;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class PdfReportGeneratorTest {
  @Test void generate_returns_non_empty_bytes() throws Exception {
    var s = new StatsData.Summary(150, 30, 12, 45);
    var c = new StatsData.CollegeStat("A", 50, 10, 4, 15);
    var top = new StatsData.CourseEntry("AC001", "DB", 28, "A");
    var data = new StatsData(s, Collections.singletonList(c), Collections.singletonList(top));
    byte[] pdf = PdfReportGenerator.generate(data);
    assertTrue(pdf.length > 0);
    // PDF magic bytes
    assertEquals('%', pdf[0]);
    assertEquals('P', pdf[1]);
    assertEquals('D', pdf[2]);
    assertEquals('F', pdf[3]);
  }

  @Test void empty_data_generates_valid_pdf() throws Exception {
    var data = new StatsData(new StatsData.Summary(0, 0, 0, 0),
        Collections.emptyList(), Collections.emptyList());
    byte[] pdf = PdfReportGenerator.generate(data);
    assertTrue(pdf.length > 0);
  }
}
```

Run: `mvn -pl analytics -am test -Dtest=PdfReportGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL → 实现后 PASS.

- [ ] **Step 5: PASS + commit**

```bash
git add analytics/src/
git commit -m "feat(analytics): apache fop pdf report generator with xsl-fo template"
```

---

## Task 5: 实时监控组件 — MonitorPanel + MonitorPoller

**Files:**
- Create: `analytics/src/main/java/analytics/monitor/MonitorPoller.java`
- Create: `analytics/src/main/java/analytics/ui/MonitorPanel.java`
- Test: `analytics/src/test/java/analytics/monitor/MonitorPollerTest.java`

- [ ] **Step 1: 实现 MonitorPoller**

```java
// analytics/src/main/java/analytics/monitor/MonitorPoller.java
package analytics.monitor;

import analytics.model.MonitorSnapshot;
import analytics.model.ServerStatus;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MonitorPoller {

  private final String integrationHost;
  private final int integrationPort;
  private final Consumer<MonitorSnapshot> onUpdate;
  private Timer timer;

  public MonitorPoller(String integrationHost, int integrationPort, Consumer<MonitorSnapshot> onUpdate) {
    this.integrationHost = integrationHost;
    this.integrationPort = integrationPort;
    this.onUpdate = onUpdate;
  }

  public void start(int intervalMs) {
    timer = new Timer(intervalMs, e -> poll());
    timer.setInitialDelay(0);
    timer.start();
  }

  public void stop() {
    if (timer != null) timer.stop();
  }

  private void poll() {
    try (var sock = new Socket(integrationHost, integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.MONITOR_STATUS, java.util.UUID.randomUUID().toString(), ""));
      Message resp = Message.read(sock.getInputStream());
      if (resp.command() == Command.OK) {
        var doc = XmlIO.parse(resp.payload());
        List<ServerStatus> servers = new ArrayList<>();
        for (var obj : doc.getRootElement().elements("server")) {
          var el = (org.dom4j.Element) obj;
          servers.add(new ServerStatus(
              el.attributeValue("name"),
              Boolean.parseBoolean(el.attributeValue("online")),
              Long.parseLong(el.attributeValue("latencyMs")),
              Integer.parseInt(el.attributeValue("requestCount")),
              Integer.parseInt(el.attributeValue("errorCount")),
              Long.parseLong(el.attributeValue("uptimeSeconds"))));
        }
        onUpdate.accept(new MonitorSnapshot(servers));
      }
    } catch (IOException e) {
      // silently skip this cycle
    }
  }
}
```

- [ ] **Step 2: 实现 MonitorPanel（Swing 指示灯面板）**

```java
// analytics/src/main/java/analytics/ui/MonitorPanel.java
package analytics.ui;

import analytics.model.MonitorSnapshot;
import javax.swing.*;
import java.awt.*;

public class MonitorPanel extends JPanel {

  private final JLabel indicatorA = createIndicator();
  private final JLabel indicatorB = createIndicator();
  private final JLabel indicatorC = createIndicator();
  private final JLabel indicatorI = createIndicator();
  private final JLabel infoLabel = new JLabel("等待首次探测...");

  public MonitorPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("实时监控"));
    add(createRow("Integration Server", indicatorI));
    add(createRow("College A", indicatorA));
    add(createRow("College B", indicatorB));
    add(createRow("College C", indicatorC));
    add(infoLabel);
  }

  public void update(MonitorSnapshot snapshot) {
    SwingUtilities.invokeLater(() -> {
      for (var s : snapshot.servers()) {
        JLabel indicator = switch (s.name()) {
          case "A" -> indicatorA;
          case "B" -> indicatorB;
          case "C" -> indicatorC;
          default -> indicatorI;
        };
        indicator.setForeground(s.online() ? Color.GREEN : Color.RED);
      }
      infoLabel.setText("最后更新: " + snapshot.timestamp());
    });
  }

  private JPanel createRow(String label, JLabel indicator) {
    var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    row.add(indicator);
    row.add(new JLabel(label));
    return row;
  }

  private static JLabel createIndicator() {
    var l = new JLabel("●");
    l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
    l.setForeground(Color.GRAY);
    return l;
  }
}
```

- [ ] **Step 3: 写 MonitorPoller 单元测试（mock Socket）**

Mock 模式：启动一个 ServerSocket → 返回模拟 MONITOR_STATUS 响应 → 验证 onUpdate 被调用。

```java
// analytics/src/test/java/analytics/monitor/MonitorPollerTest.java
package analytics.monitor;

import analytics.model.MonitorSnapshot;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class MonitorPollerTest {
  @Test void poll_receives_server_statuses() throws Exception {
    try (var server = new ServerSocket(0)) {
      var latch = new CountDownLatch(1);
      var ref = new Object() { MonitorSnapshot snapshot; };

      var poller = new MonitorPoller("127.0.0.1", server.getLocalPort(), snap -> {
        ref.snapshot = snap;
        latch.countDown();
      });

      // Simulate Integration Server response
      new Thread(() -> {
        try (var sock = server.accept()) {
          Message req = Message.read(sock.getInputStream());
          Message.write(sock.getOutputStream(), Message.ok(req.requestId(),
              "<monitor timestamp=\"2026-05-28T10:00:00Z\">" +
              "<server name=\"I\" online=\"true\" latencyMs=\"12\" requestCount=\"250\" errorCount=\"0\" uptimeSeconds=\"3600\"/>" +
              "<server name=\"A\" online=\"true\" latencyMs=\"15\" requestCount=\"180\" errorCount=\"2\" uptimeSeconds=\"3500\"/>" +
              "<server name=\"B\" online=\"false\" latencyMs=\"0\" requestCount=\"0\" errorCount=\"0\" uptimeSeconds=\"0\"/>" +
              "<server name=\"C\" online=\"true\" latencyMs=\"8\" requestCount=\"200\" errorCount=\"1\" uptimeSeconds=\"3400\"/>" +
              "</monitor>"));
        } catch (Exception ignore) {}
      }).start();

      poller.start(100);
      assertTrue(latch.await(3, TimeUnit.SECONDS));
      assertNotNull(ref.snapshot);
      assertEquals(4, ref.snapshot.servers().size());
      assertTrue(ref.snapshot.servers().get(0).online());
      assertFalse(ref.snapshot.servers().get(2).online()); // B is offline
      poller.stop();
    }
  }
}
```

- [ ] **Step 4: PASS + commit**

```bash
mvn -pl analytics -am test -Dtest=MonitorPollerTest -Dsurefire.failIfNoSpecifiedTests=false
git add analytics/src/
git commit -m "feat(analytics): monitor poller and indicator panel"
```

---

## Task 6: DashboardDialog — 分析中心弹窗

**Files:**
- Create: `analytics/src/main/java/analytics/ui/DashboardPanel.java`
- Create: `analytics/src/main/java/analytics/ui/DashboardDialog.java`
- Test: `analytics/src/test/java/analytics/ui/DashboardPanelTest.java`

- [ ] **Step 1: 实现 DashboardPanel（组合面板）**

```java
// analytics/src/main/java/analytics/ui/DashboardPanel.java
package analytics.ui;

import analytics.chart.ChartFactory;
import analytics.model.MonitorSnapshot;
import analytics.model.StatsData;
import org.jfree.chart.ChartPanel;

import javax.swing.*;
import java.awt.*;

public class DashboardPanel extends JPanel {

  private final JPanel chartGrid = new JPanel(new GridLayout(1, 2, 8, 8));
  private final MonitorPanel monitorPanel = new MonitorPanel();
  private final JLabel statusLabel = new JLabel(" ");

  private StatsData currentData;

  public DashboardPanel() {
    setLayout(new BorderLayout());
    add(new JScrollPane(chartGrid), BorderLayout.CENTER);
    var bottom = new JPanel(new BorderLayout());
    bottom.add(monitorPanel, BorderLayout.CENTER);
    bottom.add(statusLabel, BorderLayout.SOUTH);
    add(bottom, BorderLayout.SOUTH);
  }

  public void showStats(StatsData data) {
    this.currentData = data;
    chartGrid.removeAll();
    chartGrid.add(new ChartPanel(ChartFactory.createComparisonBarChart(data)));
    chartGrid.add(new ChartPanel(ChartFactory.createEnrollmentPieChart(data)));
    chartGrid.revalidate();
    chartGrid.repaint();
    statusLabel.setText("数据加载完成");
  }

  public void updateMonitor(MonitorSnapshot snapshot) {
    monitorPanel.update(snapshot);
  }

  public StatsData getCurrentData() { return currentData; }
}
```

- [ ] **Step 2: 实现 DashboardDialog**

```java
// analytics/src/main/java/analytics/ui/DashboardDialog.java
package analytics.ui;

import analytics.model.MonitorSnapshot;
import analytics.monitor.MonitorPoller;
import analytics.model.StatsData;
import analytics.pdf.PdfReportGenerator;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.UUID;
import java.util.function.Function;

public class DashboardDialog extends JDialog {

  private final DashboardPanel dashboardPanel;
  private final MonitorPoller poller;
  private final Function<Message, Message> sender; // inject network layer
  private Timer autoRefreshTimer;

  public DashboardDialog(Frame owner, String serverHost, int serverPort,
                         Function<Message, Message> sender,
                         String integrationHost, int integrationPort) {
    super(owner, "集成分析中心", false);
    this.sender = sender;
    this.dashboardPanel = new DashboardPanel();
    this.poller = new MonitorPoller(integrationHost, integrationPort, dashboardPanel::updateMonitor);

    setLayout(new BorderLayout());
    add(dashboardPanel, BorderLayout.CENTER);

    var buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
    var refreshBtn = new JButton("刷新图表");
    var exportBtn = new JButton("导出 PDF");
    var closeBtn = new JButton("关闭");
    refreshBtn.addActionListener(e -> loadAnalyticsData());
    exportBtn.addActionListener(e -> exportPdf());
    closeBtn.addActionListener(e -> dispose());
    buttonPanel.add(refreshBtn);
    buttonPanel.add(exportBtn);
    buttonPanel.add(closeBtn);
    add(buttonPanel, BorderLayout.SOUTH);

    setSize(900, 700);
    setLocationRelativeTo(owner);
  }

  @Override public void setVisible(boolean v) {
    if (v) {
      loadAnalyticsData();
      poller.start(5000); // 5s interval
    } else {
      poller.stop();
    }
    super.setVisible(v);
  }

  private void loadAnalyticsData() {
    new Thread(() -> {
      try {
        Message resp = sender.apply(new Message(Command.ANALYTICS_EXPORT,
            UUID.randomUUID().toString(), ""));
        if (resp.command() == Command.OK) {
          var data = StatsData.fromXml(XmlIO.parse(resp.payload()));
          SwingUtilities.invokeLater(() -> dashboardPanel.showStats(data));
        }
      } catch (Exception e) {
        SwingUtilities.invokeLater(() ->
            dashboardPanel.statusLabel.setText("加载失败: " + e.getMessage()));
      }
    }).start();
  }

  private void exportPdf() {
    var data = dashboardPanel.getCurrentData();
    if (data == null) {
      JOptionPane.showMessageDialog(this, "请先加载数据", "提示", JOptionPane.WARNING_MESSAGE);
      return;
    }
    var chooser = new JFileChooser();
    chooser.setSelectedFile(new File("analytics-report.pdf"));
    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        PdfReportGenerator.saveToFile(data, chooser.getSelectedFile());
        JOptionPane.showMessageDialog(this, "PDF 已保存到 " + chooser.getSelectedFile().getAbsolutePath());
      } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "PDF 生成失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  @Override public void dispose() {
    poller.stop();
    super.dispose();
  }
}
```

- [ ] **Step 3: commit**

```bash
git add analytics/src/
git commit -m "feat(analytics): dashboard dialog with charts, monitor, and pdf export"
```

---

## Task 7: Server 端 — Heartbeat + Monitor + Analytics handler

**Files:**
- Create: `college-a/src/main/java/college/a/server/handler/HeartbeatHandler.java`（B/C 同理）
- Create: `integration/src/main/java/integration/server/handler/MonitorHandler.java`
- Create: `integration/src/main/java/integration/server/handler/AnalyticsHandler.java`
- Modify: `common/src/main/java/cn/edu/di/protocol/Command.java`（+MONITOR_STATUS, +ANALYTICS_EXPORT, +HEARTBEAT）
- Modify: 三院的 `CollegeXServer.java`（注册 HeartbeatHandler）
- Modify: `integration/src/main/java/integration/server/IntegrationServer.java`（注册新 handler）

- [ ] **Step 1: 修改 Command 枚举**

在 `Command.java` 枚举中追加：`MONITOR_STATUS, ANALYTICS_EXPORT, HEARTBEAT`

- [ ] **Step 2: 实现 HeartbeatHandler（各院）**

```java
// college-a/src/main/java/college/a/server/handler/HeartbeatHandler.java
package college.a.server.handler;

import cn.edu.di.protocol.Message;

public class HeartbeatHandler implements Handler {
  private final String collegeCode;
  private final long startTime = System.currentTimeMillis();
  private int requestCount = 0;
  private int errorCount = 0;

  public HeartbeatHandler(String collegeCode) { this.collegeCode = collegeCode; }

  @Override
  public Message handle(Message request) {
    requestCount++;
    long uptime = (System.currentTimeMillis() - startTime) / 1000;
    return Message.ok(request.requestId(),
        "<heartbeat college=\"" + collegeCode + "\""
        + " online=\"true\" latencyMs=\"0\""
        + " requestCount=\"" + requestCount + "\""
        + " errorCount=\"" + errorCount + "\""
        + " uptimeSeconds=\"" + uptime + "\"/>");
  }

  public void incrementErrors() { errorCount++; }
}
```

各院注册：
```java
.register(Command.HEARTBEAT, new HeartbeatHandler(config.collegeCode()))
```

- [ ] **Step 3: 实现 MonitorHandler（Integration）**

```java
// integration/src/main/java/integration/server/handler/MonitorHandler.java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;

import java.util.UUID;

public class MonitorHandler implements Handler {

  private final CollegeClient clientA, clientB, clientC;
  private final long startTime = System.currentTimeMillis();
  private int requestCount = 0;

  public MonitorHandler(CollegeClient ca, CollegeClient cb, CollegeClient cc) {
    this.clientA = ca; this.clientB = cb; this.clientC = cc;
  }

  @Override
  public Message handle(Message req) {
    requestCount++;
    long uptime = (System.currentTimeMillis() - startTime) / 1000;

    StringBuilder xml = new StringBuilder("<monitor timestamp=\"" + java.time.Instant.now() + "\">");

    // Self check
    xml.append("<server name=\"I\" online=\"true\" latencyMs=\"0\" requestCount=\"")
       .append(requestCount).append("\" errorCount=\"0\" uptimeSeconds=\"").append(uptime).append("\"/>");

    // Check each college
    checkCollege(xml, clientA, "A");
    checkCollege(xml, clientB, "B");
    checkCollege(xml, clientC, "C");

    xml.append("</monitor>");
    return Message.ok(req.requestId(), xml.toString());
  }

  private void checkCollege(StringBuilder xml, CollegeClient client, String name) {
    try {
      long start = System.currentTimeMillis();
      Message resp = client.send(new Message(Command.HEARTBEAT, UUID.randomUUID().toString(), ""));
      long latency = System.currentTimeMillis() - start;
      if (resp.command() == Command.OK) {
        xml.append(resp.payload()); // reuse college's heartbeat response
        return;
      }
    } catch (Exception ignore) {}
    xml.append("<server name=\"").append(name)
        .append("\" online=\"false\" latencyMs=\"0\" requestCount=\"0\" errorCount=\"0\" uptimeSeconds=\"0\"/>");
  }
}
```

- [ ] **Step 4: 实现 AnalyticsHandler（Integration）**

扩展已有的 `StatsGlobalHandler` 聚合逻辑，返回增强的 `<analyticsReport>` XML（含 topCourses）。

```java
// integration/src/main/java/integration/server/handler/AnalyticsHandler.java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import integration.net.CollegeClient;
import org.dom4j.Element;
import java.util.*;
import java.util.stream.Collectors;

public class AnalyticsHandler implements Handler {

  private final CollegeClient clientA, clientB, clientC;

  public AnalyticsHandler(CollegeClient ca, CollegeClient cb, CollegeClient cc) {
    this.clientA = ca; this.clientB = cb; this.clientC = cc;
  }

  @Override
  public Message handle(Message req) {
    try {
      int totalStudents = 0, totalCourses = 0, totalShared = 0, totalCross = 0;
      var collegeStats = new ArrayList<CollegeStat>();
      var allCourses = new ArrayList<CourseEntry>();

      for (var entry : Map.of("A", clientA, "B", clientB, "C", clientC).entrySet()) {
        Message resp = entry.getValue().send(
            new Message(Command.STATS_PULL, UUID.randomUUID().toString(), ""));
        if (resp.command() != Command.OK) continue;
        Element root = XmlIO.parse(resp.payload()).getRootElement();
        String code = entry.getKey();
        int s = Integer.parseInt(root.elementText("studentCount"));
        int c = Integer.parseInt(root.elementText("courseCount"));
        int sh = Integer.parseInt(root.elementText("sharedCount"));
        int cr = Integer.parseInt(root.elementText("crossEnrollmentCount"));
        totalStudents += s; totalCourses += c; totalShared += sh; totalCross += cr;
        collegeStats.add(new CollegeStat(code, s, c, sh, cr));

        Element coursesEl = root.element("courses");
        if (coursesEl != null) {
          for (var o : coursesEl.elements("course")) {
            var ce = (Element) o;
            allCourses.add(new CourseEntry(
                ce.attributeValue("id"), ce.attributeValue("name"),
                Integer.parseInt(ce.attributeValue("enrollments")), code));
          }
        }
      }

      allCourses.sort((a, b) -> Integer.compare(b.enrollments(), a.enrollments()));
      var top10 = allCourses.stream().limit(10).collect(Collectors.toList());

      var summary = new Summary(totalStudents, totalCourses, totalShared, totalCross);
      var data = new StatsData(summary, collegeStats, top10);
      return Message.ok(req.requestId(), data.toXml());
    } catch (Exception e) {
      return Message.err(req.requestId(), "ANALYTICS_FAILED", e.getMessage());
    }
  }

  private record Summary(int totalStudents, int totalCourses, int totalShared, int totalCross) {}
  private record CollegeStat(String code, int students, int courses, int shared, int cross) {}
  private record CourseEntry(String id, String name, int enrollments, String origin) {}
}
```

注意：`StatsData` 在 `analytics` 模块，Integration 不依赖 analytics。所以 AnalyticsHandler 内部用 `private` record 计算，序列化为 XML 字符串返回（与 StatsData.toXml() 格式一致）。[待执行时决定是否抽象通用 StatsData 到 common 模块]。

- [ ] **Step 5: 注册 handler + commit**

在各 Server main() 中注册新 handler，编译验证。

```bash
mvn -q -DskipTests compile
git add common/src/ college-a/src/ college-b/src/ college-c/src/ integration/src/
git commit -m "feat(server): heartbeat, monitor, and analytics handlers"
```

---

## Task 8: Client 集成 — 分析中心按钮

**Files:**
- Modify: `client/src/main/java/client/ui/CourseListFrame.java`
- Modify: `client/pom.xml`（加 analytics 依赖）

- [ ] **Step 1: client pom.xml 加 analytics 依赖**

```xml
<dependency><groupId>cn.edu.di</groupId><artifactId>analytics</artifactId><version>1.0.0-SNAPSHOT</version></dependency>
```

- [ ] **Step 2: CourseListFrame 加"分析中心"按钮**

在按钮面板添加：

```java
var analyticsButton = new JButton("分析中心");
analyticsButton.addActionListener(e -> openAnalytics());
buttonPanel.add(analyticsButton);
```

`openAnalytics()` 方法：

```java
private void openAnalytics() {
  String integrationHost = System.getProperty("integration.host", "127.0.0.1");
  int integrationPort = Integer.parseInt(System.getProperty("integration.port", "9100"));
  var dialog = new DashboardDialog(
      (Frame) SwingUtilities.getWindowAncestor(this),
      this.client.getHost(), this.client.getPort(),
      msg -> { try { return client.send(msg); } catch (Exception e) { return Message.err("0", "SEND_FAILED", e.getMessage()); } },
      integrationHost, integrationPort);
  dialog.setVisible(true);
}
```

- [ ] **Step 3: commit**

```bash
git add client/src/ client/pom.xml
git commit -m "feat(client): analytics center button in course list frame"
```

---

## Task 9: start-all.sh 更新 + 端到端验证

**Files:**
- Modify: `scripts/start-all.sh`（加 analytics classpath）

- [ ] **Step 1: 更新 start-all.sh**

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -pl analytics
```

客户端启动提示中 classpath 加入 analytics jar。

- [ ] **Step 2: 编译验证 + 启动测试**

```bash
mvn -q -DskipTests install
./scripts/start-all.sh
# 启动客户端，点击"分析中心"，验证图表和监控面板
```

- [ ] **Step 3: commit**

```bash
git add scripts/start-all.sh
git commit -m "build: add analytics module to start-all.sh"
```

---

## Plan 4 验收

- [ ] `mvn test` 全部通过（含 analytics 模块 10+ 新测试）
- [ ] "分析中心"按钮可打开 DashboardDialog
- [ ] 至少 3 种图表（柱状图、饼图、条形图）正常渲染
- [ ] 实时监控面板 5s 刷新，四台 Server 在线状态指示灯正确
- [ ] 点击"导出 PDF"生成有效 PDF 文件
- [ ] `./scripts/start-all.sh` 正常运行
- [ ] PDF 中文字符不乱码
