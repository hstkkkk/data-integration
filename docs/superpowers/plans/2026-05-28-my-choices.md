# 「我的选课」（含跨院）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 GUI 增加「我的选课」按钮，弹窗展示当前学生本院选课（本院字段命名）+ 跨院选课（带 origin 标记），即便部分外院不可达本院数据仍能呈现。

**Architecture:** 复用现有 `STATS_GLOBAL → STATS_PULL` 的 fan-out 模式：客户端发 `LIST_MY_CHOICES` 到 home college；home college 查本院 join → 转发 `PULL_MY_CHOICES` 到 integration；integration 向其他两院广播 `ASK_MY_CHOICES`，每院本院字段返回 → integration 用专用 `formatX-myChoice.xsl` 翻成统一 `<classes>` 加 sno/grade，过专用 XSD，合并 + 错误标记后回 home college；home college 拼装 `<myChoices>` 返回客户端。

**Tech Stack:** Java 17, DOM4J 2.1.4, Xerces 2.12.2 (XSD), JUnit 5.10.2, Mockito 5.11.0, Swing。

**前置假设：** 你在 `data-integration` 仓库根目录工作，main 分支可直接提交。每个任务做完都要 `mvn -DskipTests install` 确保子模块互相能引到，再 `mvn test` 跑全测。

**总览（共 18 个任务）：**

1. 协议：`Command` 枚举加 3 个值
2. XSD：`formatMyChoice.xsd`
3-5. 三院 `AskMyChoicesHandler`（本院字段 + Choice/Course join）+ 单测
6-8. 三院 formatX-myChoice.xsl（本院 → 统一）+ 单测
9. integration `PullMyChoicesHandler` + 单测
10. integration `IntegrationServer` 注册
11-13. 三院 unifiedMyChoiceToX.xsl（统一 → 本院）+ 单测
14-16. 三院 `ListMyChoicesHandler`（home 拼装）+ 单测
17. 三院 server 注册两个新 handler
18. 客户端 `MyChoicesDialog` + `CourseListFrame` 按钮

---

## Task 1: 协议层加 3 个 Command 枚举

**Files:**
- Modify: `common/src/main/java/cn/edu/di/protocol/Command.java`

- [ ] **Step 1: 编辑 Command 枚举**

打开 `common/src/main/java/cn/edu/di/protocol/Command.java`，在第二行末尾追加 3 个值：

```java
package cn.edu.di.protocol;

public enum Command {
    LOGIN, LIST_LOCAL_COURSES, LIST_SHARED_COURSES, ENROLL, WITHDRAW, STATS_GLOBAL,
    FETCH_SHARED_COURSES, ASK_COURSE_INFO, SEND_COURSE_INFO,
    CROSS_ENROLL, CROSS_WITHDRAW, APPLY_CHOICE, REVOKE_CHOICE,
    STATS_PULL, STATS_DATA,
    LIST_MY_CHOICES, PULL_MY_CHOICES, ASK_MY_CHOICES,
    PING, OK, ERR, UNKNOWN;

    public static Command parse(String s) {
        try { return Command.valueOf(s); }
        catch (IllegalArgumentException e) { return UNKNOWN; }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn -pl common compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add common/src/main/java/cn/edu/di/protocol/Command.java
git commit -m "feat(protocol): add LIST_MY_CHOICES / PULL_MY_CHOICES / ASK_MY_CHOICES"
```

---

## Task 2: 新建 formatMyChoice.xsd

**Files:**
- Create: `common/src/main/resources/schema/formatMyChoice.xsd`
- Test: `common/src/test/java/cn/edu/di/xml/MyChoiceSchemaTest.java`

- [ ] **Step 1: 写失败测试**

新建 `common/src/test/java/cn/edu/di/xml/MyChoiceSchemaTest.java`：

```java
package cn.edu.di.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyChoiceSchemaTest {
  private static final String VALID =
      "<classes>"
      + "<class origin=\"B\">"
      + "<id>BC003</id><name>OS</name><time>32</time><score>3.0</score>"
      + "<teacher>Wang</teacher><location>B201</location>"
      + "<share>Y</share><sno>AS001</sno><grade></grade>"
      + "</class></classes>";

  @Test
  void accepts_valid_my_choice_xml() {
    var v = XsdValidator.fromClasspath("/schema/formatMyChoice.xsd");
    var r = v.validate(VALID);
    assertTrue(r.valid(), () -> "errors: " + r.errors());
  }

  @Test
  void rejects_when_sno_missing() {
    String bad = VALID.replace("<sno>AS001</sno>", "");
    var v = XsdValidator.fromClasspath("/schema/formatMyChoice.xsd");
    assertFalse(v.validate(bad).valid());
  }
}
```

- [ ] **Step 2: 跑测试，确认失败**

```bash
mvn -pl common test -Dtest=MyChoiceSchemaTest
```

Expected: FAIL，因为 `formatMyChoice.xsd` 不存在。

- [ ] **Step 3: 创建 XSD**

新建 `common/src/main/resources/schema/formatMyChoice.xsd`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="classes">
    <xs:complexType><xs:sequence>
      <xs:element name="class" minOccurs="0" maxOccurs="unbounded">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="id"       type="xs:string"/>
            <xs:element name="name"     type="xs:string"/>
            <xs:element name="time"     type="xs:decimal"/>
            <xs:element name="score"    type="xs:decimal"/>
            <xs:element name="teacher"  type="xs:string"/>
            <xs:element name="location" type="xs:string"/>
            <xs:element name="share">
              <xs:simpleType><xs:restriction base="xs:string">
                <xs:enumeration value="Y"/><xs:enumeration value="N"/>
              </xs:restriction></xs:simpleType>
            </xs:element>
            <xs:element name="sno"   type="xs:string"/>
            <xs:element name="grade" type="xs:string"/>
          </xs:sequence>
          <xs:attribute name="origin" use="required">
            <xs:simpleType><xs:restriction base="xs:string">
              <xs:enumeration value="A"/><xs:enumeration value="B"/><xs:enumeration value="C"/>
            </xs:restriction></xs:simpleType>
          </xs:attribute>
        </xs:complexType>
      </xs:element>
    </xs:sequence></xs:complexType>
  </xs:element>
</xs:schema>
```

- [ ] **Step 4: 跑测试，确认通过**

```bash
mvn -pl common test -Dtest=MyChoiceSchemaTest
```

Expected: PASS, 2/2。

- [ ] **Step 5: 提交**

```bash
git add common/src/main/resources/schema/formatMyChoice.xsd \
        common/src/test/java/cn/edu/di/xml/MyChoiceSchemaTest.java
git commit -m "feat(xsd): formatMyChoice.xsd for unified my-choice <classes> with sno/grade"
```

---

(plan 续：Task 3-18 见下文)

---

## Task 3: 三院 AskMyChoicesHandler — A 院

**Files:**
- Create: `college-a/src/main/java/college/a/server/handler/AskMyChoicesHandler.java`
- Test: `college-a/src/test/java/college/a/server/handler/AskMyChoicesHandlerTest.java`

- [ ] **Step 1: 写失败测试**

新建 `college-a/src/test/java/college/a/server/handler/AskMyChoicesHandlerTest.java`：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AskMyChoicesHandlerTest {
  @Test
  void returns_local_field_xml_for_student_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("AS001")).thenReturn(List.of(
        new ChoiceDao.Row("AC001", "AS001", null, "A")));
    when(courseDao.findById("AC001")).thenReturn(Optional.of(
        new CourseDao.Row("AC001", "Math", 48, BigDecimal.valueOf(3),
            "Zhao", "A101", false)));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>AS001</sno>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"AS001\">"));
    assertTrue(res.payload().contains("<课程编号>AC001</课程编号>"));
    assertTrue(res.payload().contains("<授课老师>Zhao</授课老师>"));
    assertTrue(res.payload().contains("<学生编号>AS001</学生编号>"));
  }

  @Test
  void returns_empty_set_when_no_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("AS999")).thenReturn(List.of());
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>AS999</sno>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"AS999\">"));
  }

  @Test
  void returns_err_on_dao_failure() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db down"));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>AS001</sno>"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-a test -Dtest=AskMyChoicesHandlerTest
```

Expected: 编译失败（class 不存在）。

- [ ] **Step 3: 实现**

新建 `college-a/src/main/java/college/a/server/handler/AskMyChoicesHandler.java`：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

public class AskMyChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CourseDao courseDao;

  public AskMyChoicesHandler(ChoiceDao choiceDao, CourseDao courseDao) {
    this.choiceDao = choiceDao;
    this.courseDao = courseDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      String sno = XmlIO.parse(req.payload()).getRootElement().getText().trim();
      var rows = choiceDao.findByStudent(sno);
      Document doc = DocumentHelper.createDocument();
      Element root = doc.addElement("myChoiceSet").addAttribute("sno", sno);
      for (var ch : rows) {
        var courseOpt = courseDao.findById(ch.courseId());
        if (courseOpt.isEmpty()) continue;
        var course = courseOpt.get();
        Element c = root.addElement("课程");
        c.addElement("课程编号").setText(course.id());
        c.addElement("课程名称").setText(course.name());
        c.addElement("课时").setText(Integer.toString(course.hours()));
        c.addElement("学分").setText(course.score().toPlainString());
        c.addElement("授课老师").setText(course.teacher());
        c.addElement("授课地点").setText(course.location());
        c.addElement("学生编号").setText(ch.studentId());
        c.addElement("成绩").setText(ch.score() == null ? "" : ch.score());
      }
      return Message.ok(req.requestId(), XmlIO.toPrettyString(doc));
    } catch (RuntimeException e) {
      return Message.err(req.requestId(), "LOCAL_QUERY_FAILED", e.getMessage());
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-a test -Dtest=AskMyChoicesHandlerTest
```

Expected: PASS, 3/3。

- [ ] **Step 5: 提交**

```bash
git add college-a/src/main/java/college/a/server/handler/AskMyChoicesHandler.java \
        college-a/src/test/java/college/a/server/handler/AskMyChoicesHandlerTest.java
git commit -m "feat(college-a): AskMyChoicesHandler returns A's local choice XML for a sno"
```

---

## Task 4: 三院 AskMyChoicesHandler — B 院

**Files:**
- Create: `college-b/src/main/java/college/b/server/handler/AskMyChoicesHandler.java`
- Test: `college-b/src/test/java/college/b/server/handler/AskMyChoicesHandlerTest.java`

- [ ] **Step 1: 写失败测试**

新建 `college-b/src/test/java/college/b/server/handler/AskMyChoicesHandlerTest.java`：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AskMyChoicesHandlerTest {
  @Test
  void returns_local_field_xml_for_student_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("BS001")).thenReturn(List.of(
        new ChoiceDao.Row("BC003", "BS001", null)));
    when(courseDao.findById("BC003")).thenReturn(Optional.of(
        new CourseDao.Row("BC003", "OS", 32, BigDecimal.valueOf(3),
            "Wang", "B201", true)));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>BS001</sno>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"BS001\">"));
    assertTrue(res.payload().contains("<编号>BC003</编号>"));
    assertTrue(res.payload().contains("<老师>Wang</老师>"));
    assertTrue(res.payload().contains("<学号>BS001</学号>"));
  }

  @Test
  void returns_empty_set_when_no_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("BS999")).thenReturn(List.of());
    var h = new AskMyChoicesHandler(choiceDao, courseDao);
    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>BS999</sno>"));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"BS999\">"));
  }

  @Test
  void returns_err_on_dao_failure() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db down"));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);
    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>BS001</sno>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-b test -Dtest=AskMyChoicesHandlerTest
```

Expected: 编译失败。

- [ ] **Step 3: 实现**

新建 `college-b/src/main/java/college/b/server/handler/AskMyChoicesHandler.java`：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

public class AskMyChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CourseDao courseDao;

  public AskMyChoicesHandler(ChoiceDao choiceDao, CourseDao courseDao) {
    this.choiceDao = choiceDao;
    this.courseDao = courseDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      String sno = XmlIO.parse(req.payload()).getRootElement().getText().trim();
      var rows = choiceDao.findByStudent(sno);
      Document doc = DocumentHelper.createDocument();
      Element root = doc.addElement("myChoiceSet").addAttribute("sno", sno);
      for (var ch : rows) {
        var courseOpt = courseDao.findById(ch.courseId());
        if (courseOpt.isEmpty()) continue;
        var course = courseOpt.get();
        Element c = root.addElement("课程");
        c.addElement("编号").setText(course.id());
        c.addElement("名称").setText(course.name());
        c.addElement("课时").setText(Integer.toString(course.hours()));
        c.addElement("学分").setText(course.score().toPlainString());
        c.addElement("老师").setText(course.teacher());
        c.addElement("地点").setText(course.location());
        c.addElement("学号").setText(ch.studentId());
        c.addElement("得分").setText(ch.score() == null ? "" : ch.score());
      }
      return Message.ok(req.requestId(), XmlIO.toPrettyString(doc));
    } catch (RuntimeException e) {
      return Message.err(req.requestId(), "LOCAL_QUERY_FAILED", e.getMessage());
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-b test -Dtest=AskMyChoicesHandlerTest
```

Expected: PASS, 3/3。

- [ ] **Step 5: 提交**

```bash
git add college-b/src/main/java/college/b/server/handler/AskMyChoicesHandler.java \
        college-b/src/test/java/college/b/server/handler/AskMyChoicesHandlerTest.java
git commit -m "feat(college-b): AskMyChoicesHandler returns B's local choice XML for a sno"
```

---

## Task 5: 三院 AskMyChoicesHandler — C 院

**Files:**
- Create: `college-c/src/main/java/college/c/server/handler/AskMyChoicesHandler.java`
- Test: `college-c/src/test/java/college/c/server/handler/AskMyChoicesHandlerTest.java`

- [ ] **Step 1: 写失败测试**

新建 `college-c/src/test/java/college/c/server/handler/AskMyChoicesHandlerTest.java`：

```java
package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AskMyChoicesHandlerTest {
  @Test
  void returns_local_field_xml_for_student_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("CS001")).thenReturn(List.of(
        new ChoiceDao.Row("CC005", "CS001", null)));
    when(courseDao.findById("CC005")).thenReturn(Optional.of(
        new CourseDao.Row("CC005", "Net", 32, BigDecimal.valueOf(2),
            "Li", "C301", true)));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>CS001</sno>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"CS001\">"));
    assertTrue(res.payload().contains("<Cno>CC005</Cno>"));
    assertTrue(res.payload().contains("<Tec>Li</Tec>"));
    assertTrue(res.payload().contains("<Sno>CS001</Sno>"));
  }

  @Test
  void returns_empty_set_when_no_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("CS999")).thenReturn(List.of());
    var h = new AskMyChoicesHandler(choiceDao, courseDao);
    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>CS999</sno>"));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"CS999\">"));
  }

  @Test
  void returns_err_on_dao_failure() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db down"));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);
    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>CS001</sno>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-c test -Dtest=AskMyChoicesHandlerTest
```

Expected: 编译失败。

- [ ] **Step 3: 实现**

新建 `college-c/src/main/java/college/c/server/handler/AskMyChoicesHandler.java`：

```java
package college.c.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

public class AskMyChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CourseDao courseDao;

  public AskMyChoicesHandler(ChoiceDao choiceDao, CourseDao courseDao) {
    this.choiceDao = choiceDao;
    this.courseDao = courseDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      String sno = XmlIO.parse(req.payload()).getRootElement().getText().trim();
      var rows = choiceDao.findByStudent(sno);
      Document doc = DocumentHelper.createDocument();
      Element root = doc.addElement("myChoiceSet").addAttribute("sno", sno);
      for (var ch : rows) {
        var courseOpt = courseDao.findById(ch.courseId());
        if (courseOpt.isEmpty()) continue;
        var course = courseOpt.get();
        Element c = root.addElement("course");
        c.addElement("Cno").setText(course.id());
        c.addElement("Cnm").setText(course.name());
        c.addElement("Ctm").setText(Integer.toString(course.hours()));
        c.addElement("Cpt").setText(course.score().toPlainString());
        c.addElement("Tec").setText(course.teacher());
        c.addElement("Pla").setText(course.location());
        c.addElement("Sno").setText(ch.studentId());
        c.addElement("Grd").setText(ch.grade() == null ? "" : ch.grade());
      }
      return Message.ok(req.requestId(), XmlIO.toPrettyString(doc));
    } catch (RuntimeException e) {
      return Message.err(req.requestId(), "LOCAL_QUERY_FAILED", e.getMessage());
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-c test -Dtest=AskMyChoicesHandlerTest
```

Expected: PASS, 3/3。

- [ ] **Step 5: 提交**

```bash
git add college-c/src/main/java/college/c/server/handler/AskMyChoicesHandler.java \
        college-c/src/test/java/college/c/server/handler/AskMyChoicesHandlerTest.java
git commit -m "feat(college-c): AskMyChoicesHandler returns C's local choice XML for a sno"
```

---

## Task 6: formatA-myChoice.xsl + 单测

**Files:**
- Create: `integration/src/main/resources/xsl/formatA-myChoice.xsl`
- Test: `integration/src/test/java/integration/transform/MyChoiceXslTest.java`

- [ ] **Step 1: 写失败测试**

新建 `integration/src/test/java/integration/transform/MyChoiceXslTest.java`：

```java
package integration.transform;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyChoiceXslTest {
  @Test void formatA_myChoice_translates_to_unified_classes() {
    var t = XsltTransformer.fromClasspath("/xsl/formatA-myChoice.xsl");
    String in = "<myChoiceSet sno=\"AS001\">"
        + "<课程><课程编号>AC001</课程编号><课程名称>Math</课程名称>"
        + "<课时>48</课时><学分>3</学分>"
        + "<授课老师>Zhao</授课老师><授课地点>A101</授课地点>"
        + "<学生编号>AS001</学生编号><成绩></成绩></课程>"
        + "</myChoiceSet>";
    String out = t.transform(in);
    assertTrue(out.contains("<class origin=\"A\">"));
    assertTrue(out.contains("<id>AC001</id>"));
    assertTrue(out.contains("<sno>AS001</sno>"));
    assertTrue(out.contains("<grade></grade>") || out.contains("<grade/>"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl integration test -Dtest=MyChoiceXslTest
```

Expected: FAIL（XSL 不存在）。

- [ ] **Step 3: 创建 XSL**

新建 `integration/src/main/resources/xsl/formatA-myChoice.xsl`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="myChoiceSet">
    <classes>
      <xsl:for-each select="课程">
        <class origin="A">
          <id><xsl:value-of select="课程编号"/></id>
          <name><xsl:value-of select="课程名称"/></name>
          <time><xsl:value-of select="课时"/></time>
          <score><xsl:value-of select="学分"/></score>
          <teacher><xsl:value-of select="授课老师"/></teacher>
          <location><xsl:value-of select="授课地点"/></location>
          <share>Y</share>
          <sno><xsl:value-of select="学生编号"/></sno>
          <grade><xsl:value-of select="成绩"/></grade>
        </class>
      </xsl:for-each>
    </classes>
  </xsl:template>
</xsl:stylesheet>
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl integration test -Dtest=MyChoiceXslTest
```

Expected: PASS, 1/1。

- [ ] **Step 5: 提交**

```bash
git add integration/src/main/resources/xsl/formatA-myChoice.xsl \
        integration/src/test/java/integration/transform/MyChoiceXslTest.java
git commit -m "feat(xsl): formatA-myChoice.xsl maps A's <myChoiceSet> to unified <classes>"
```

---

## Task 7: formatB-myChoice.xsl

**Files:**
- Create: `integration/src/main/resources/xsl/formatB-myChoice.xsl`
- Modify: `integration/src/test/java/integration/transform/MyChoiceXslTest.java`

- [ ] **Step 1: 加失败测试**

在 `MyChoiceXslTest.java` 末尾（最后一个 `}` 之前）追加：

```java
  @Test void formatB_myChoice_translates_to_unified_classes() {
    var t = XsltTransformer.fromClasspath("/xsl/formatB-myChoice.xsl");
    String in = "<myChoiceSet sno=\"BS001\">"
        + "<课程><编号>BC003</编号><名称>OS</名称>"
        + "<课时>32</课时><学分>3</学分>"
        + "<老师>Wang</老师><地点>B201</地点>"
        + "<学号>BS001</学号><得分></得分></课程>"
        + "</myChoiceSet>";
    String out = t.transform(in);
    assertTrue(out.contains("<class origin=\"B\">"));
    assertTrue(out.contains("<id>BC003</id>"));
    assertTrue(out.contains("<sno>BS001</sno>"));
  }
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl integration test -Dtest=MyChoiceXslTest
```

Expected: FAIL。

- [ ] **Step 3: 创建 XSL**

新建 `integration/src/main/resources/xsl/formatB-myChoice.xsl`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="myChoiceSet">
    <classes>
      <xsl:for-each select="课程">
        <class origin="B">
          <id><xsl:value-of select="编号"/></id>
          <name><xsl:value-of select="名称"/></name>
          <time><xsl:value-of select="课时"/></time>
          <score><xsl:value-of select="学分"/></score>
          <teacher><xsl:value-of select="老师"/></teacher>
          <location><xsl:value-of select="地点"/></location>
          <share>Y</share>
          <sno><xsl:value-of select="学号"/></sno>
          <grade><xsl:value-of select="得分"/></grade>
        </class>
      </xsl:for-each>
    </classes>
  </xsl:template>
</xsl:stylesheet>
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl integration test -Dtest=MyChoiceXslTest
```

Expected: PASS, 2/2。

- [ ] **Step 5: 提交**

```bash
git add integration/src/main/resources/xsl/formatB-myChoice.xsl \
        integration/src/test/java/integration/transform/MyChoiceXslTest.java
git commit -m "feat(xsl): formatB-myChoice.xsl maps B's <myChoiceSet> to unified <classes>"
```

---

## Task 8: formatC-myChoice.xsl

**Files:**
- Create: `integration/src/main/resources/xsl/formatC-myChoice.xsl`
- Modify: `integration/src/test/java/integration/transform/MyChoiceXslTest.java`

- [ ] **Step 1: 加失败测试**

在 `MyChoiceXslTest.java` 末尾追加：

```java
  @Test void formatC_myChoice_translates_to_unified_classes() {
    var t = XsltTransformer.fromClasspath("/xsl/formatC-myChoice.xsl");
    String in = "<myChoiceSet sno=\"CS001\">"
        + "<course><Cno>CC005</Cno><Cnm>Net</Cnm>"
        + "<Ctm>32</Ctm><Cpt>2</Cpt>"
        + "<Tec>Li</Tec><Pla>C301</Pla>"
        + "<Sno>CS001</Sno><Grd></Grd></course>"
        + "</myChoiceSet>";
    String out = t.transform(in);
    assertTrue(out.contains("<class origin=\"C\">"));
    assertTrue(out.contains("<id>CC005</id>"));
    assertTrue(out.contains("<sno>CS001</sno>"));
  }
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl integration test -Dtest=MyChoiceXslTest
```

Expected: FAIL。

- [ ] **Step 3: 创建 XSL**

新建 `integration/src/main/resources/xsl/formatC-myChoice.xsl`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="myChoiceSet">
    <classes>
      <xsl:for-each select="course">
        <class origin="C">
          <id><xsl:value-of select="Cno"/></id>
          <name><xsl:value-of select="Cnm"/></name>
          <time><xsl:value-of select="Ctm"/></time>
          <score><xsl:value-of select="Cpt"/></score>
          <teacher><xsl:value-of select="Tec"/></teacher>
          <location><xsl:value-of select="Pla"/></location>
          <share>Y</share>
          <sno><xsl:value-of select="Sno"/></sno>
          <grade><xsl:value-of select="Grd"/></grade>
        </class>
      </xsl:for-each>
    </classes>
  </xsl:template>
</xsl:stylesheet>
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl integration test -Dtest=MyChoiceXslTest
```

Expected: PASS, 3/3。

- [ ] **Step 5: 提交**

```bash
git add integration/src/main/resources/xsl/formatC-myChoice.xsl \
        integration/src/test/java/integration/transform/MyChoiceXslTest.java
git commit -m "feat(xsl): formatC-myChoice.xsl maps C's <myChoiceSet> to unified <classes>"
```

---

## Task 9: integration PullMyChoicesHandler

**Files:**
- Create: `integration/src/main/java/integration/server/handler/PullMyChoicesHandler.java`
- Test: `integration/src/test/java/integration/server/handler/PullMyChoicesHandlerTest.java`

- [ ] **Step 1: 写失败测试**

新建 `integration/src/test/java/integration/server/handler/PullMyChoicesHandlerTest.java`：

```java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PullMyChoicesHandlerTest {

  private static final String B_REPLY =
      "<myChoiceSet sno=\"AS001\">"
      + "<课程><编号>BC003</编号><名称>OS</名称>"
      + "<课时>32</课时><学分>3</学分>"
      + "<老师>Wang</老师><地点>B201</地点>"
      + "<学号>AS001</学号><得分></得分></课程>"
      + "</myChoiceSet>";

  private static final String C_REPLY =
      "<myChoiceSet sno=\"AS001\">"
      + "<course><Cno>CC005</Cno><Cnm>Net</Cnm>"
      + "<Ctm>32</Ctm><Cpt>2</Cpt>"
      + "<Tec>Li</Tec><Pla>C301</Pla>"
      + "<Sno>AS001</Sno><Grd></Grd></course>"
      + "</myChoiceSet>";

  private CollegeClient mockOk(String reply) throws IOException {
    var c = mock(CollegeClient.class);
    when(c.send(any())).thenReturn(Message.ok("r", reply));
    return c;
  }

  @Test
  void home_A_only_calls_B_and_C() throws IOException {
    var clientA = mock(CollegeClient.class);
    var clientB = mockOk(B_REPLY);
    var clientC = mockOk(C_REPLY);
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"AS001\" home=\"A\"/>"));

    assertEquals(Command.OK, res.command());
    verify(clientA, never()).send(any());
    verify(clientB, times(1)).send(any());
    verify(clientC, times(1)).send(any());
    assertTrue(res.payload().contains("<class origin=\"B\">"));
    assertTrue(res.payload().contains("<class origin=\"C\">"));
    assertTrue(res.payload().contains("<id>BC003</id>"));
    assertTrue(res.payload().contains("<id>CC005</id>"));
  }

  @Test
  void home_B_only_calls_A_and_C() throws IOException {
    var clientA = mockOk("<myChoiceSet sno=\"BS001\"></myChoiceSet>");
    var clientB = mock(CollegeClient.class);
    var clientC = mockOk(C_REPLY);
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"BS001\" home=\"B\"/>"));

    assertEquals(Command.OK, res.command());
    verify(clientB, never()).send(any());
    verify(clientA, times(1)).send(any());
    verify(clientC, times(1)).send(any());
  }

  @Test
  void home_C_only_calls_A_and_B() throws IOException {
    var clientA = mockOk("<myChoiceSet sno=\"CS001\"></myChoiceSet>");
    var clientB = mockOk(B_REPLY);
    var clientC = mock(CollegeClient.class);
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"CS001\" home=\"C\"/>"));

    assertEquals(Command.OK, res.command());
    verify(clientC, never()).send(any());
  }

  @Test
  void college_returns_err_yields_error_entry() throws IOException {
    var clientA = mock(CollegeClient.class);
    var clientB = mockOk(B_REPLY);
    var clientC = mock(CollegeClient.class);
    when(clientC.send(any())).thenReturn(Message.err("r", "DB_DOWN", "oops"));
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"AS001\" home=\"A\"/>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<class origin=\"B\">"));
    assertTrue(res.payload().contains("<error college=\"C\">"));
  }

  @Test
  void college_throws_yields_error_entry() throws IOException {
    var clientA = mock(CollegeClient.class);
    var clientB = mockOk(B_REPLY);
    var clientC = mock(CollegeClient.class);
    when(clientC.send(any())).thenThrow(new IOException("Connection refused"));
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"AS001\" home=\"A\"/>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<error college=\"C\">"));
  }

  @Test
  void bad_payload_returns_err() {
    var handler = new PullMyChoicesHandler(
        mock(CollegeClient.class), mock(CollegeClient.class), mock(CollegeClient.class));

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "not valid xml"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("BAD_PAYLOAD"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl integration test -Dtest=PullMyChoicesHandlerTest
```

Expected: 编译失败。

- [ ] **Step 3: 实现**

新建 `integration/src/main/java/integration/server/handler/PullMyChoicesHandler.java`：

```java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import cn.edu.di.xml.XsltTransformer;
import integration.net.CollegeClient;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.util.UUID;

public class PullMyChoicesHandler implements Handler {
  private final CollegeClient clientA;
  private final CollegeClient clientB;
  private final CollegeClient clientC;

  public PullMyChoicesHandler(CollegeClient clientA, CollegeClient clientB, CollegeClient clientC) {
    this.clientA = clientA;
    this.clientB = clientB;
    this.clientC = clientC;
  }

  @Override
  public Message handle(Message req) {
    String sno;
    String home;
    try {
      Element root = XmlIO.parse(req.payload()).getRootElement();
      sno = root.attributeValue("sno");
      home = root.attributeValue("home");
      if (sno == null || sno.isBlank() || home == null || home.isBlank()) {
        return Message.err(req.requestId(), "BAD_PAYLOAD", "missing sno or home");
      }
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }

    Document out = DocumentHelper.createDocument();
    Element resultRoot = out.addElement("crossEnrolledResult");
    Element classes = resultRoot.addElement("classes");
    Element errors = resultRoot.addElement("errors");

    if (!"A".equals(home)) {
      collect(classes, errors, clientA, "A", sno, "/xsl/formatA-myChoice.xsl");
    }
    if (!"B".equals(home)) {
      collect(classes, errors, clientB, "B", sno, "/xsl/formatB-myChoice.xsl");
    }
    if (!"C".equals(home)) {
      collect(classes, errors, clientC, "C", sno, "/xsl/formatC-myChoice.xsl");
    }
    return Message.ok(req.requestId(), XmlIO.toPrettyString(out));
  }

  private void collect(Element classes, Element errors, CollegeClient client,
                       String college, String sno, String xslPath) {
    try {
      Message ask = new Message(Command.ASK_MY_CHOICES, UUID.randomUUID().toString(),
          "<sno>" + sno + "</sno>");
      Message resp = client.send(ask);
      if (resp.command() != Command.OK) {
        addError(errors, college, resp.payload());
        return;
      }
      String unified = XsltTransformer.fromClasspath(xslPath).transform(resp.payload());
      var validation = XsdValidator.fromClasspath("/schema/formatMyChoice.xsd").validate(unified);
      if (!validation.valid()) {
        addError(errors, college, "schema invalid: " + validation.errors());
        return;
      }
      Document parsed = XmlIO.parse(unified);
      for (Object obj : parsed.getRootElement().elements("class")) {
        classes.add(((Element) obj).createCopy());
      }
    } catch (Exception e) {
      addError(errors, college, e.getMessage());
    }
  }

  private void addError(Element errors, String college, String detail) {
    Element err = errors.addElement("error").addAttribute("college", college);
    err.setText(detail == null ? "" : detail);
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl integration test -Dtest=PullMyChoicesHandlerTest
```

Expected: PASS, 6/6。

- [ ] **Step 5: 提交**

```bash
git add integration/src/main/java/integration/server/handler/PullMyChoicesHandler.java \
        integration/src/test/java/integration/server/handler/PullMyChoicesHandlerTest.java
git commit -m "feat(integration): PullMyChoicesHandler fans out ASK_MY_CHOICES, aggregates with formatX-myChoice.xsl"
```

---

## Task 10: IntegrationServer 注册 PullMyChoicesHandler

**Files:**
- Modify: `integration/src/main/java/integration/server/IntegrationServer.java`

- [ ] **Step 1: 加 import**

在文件顶部 import 区追加：

```java
import integration.server.handler.PullMyChoicesHandler;
```

- [ ] **Step 2: 注册到 router**

`main()` 方法的 router 链上追加一行（与 STATS_GLOBAL 同级）：

```java
.register(Command.PULL_MY_CHOICES, new PullMyChoicesHandler(clientA, clientB, clientC));
```

最终的 router 段长这样：

```java
IntegrationRouter router = new IntegrationRouter()
    .register(Command.PING, new PingHandler())
    .register(Command.FETCH_SHARED_COURSES, new FetchSharedCoursesHandler(clientA, clientB, clientC))
    .register(Command.CROSS_ENROLL, new CrossEnrollHandler(clientA, clientB, clientC))
    .register(Command.CROSS_WITHDRAW, new CrossWithdrawHandler(clientA, clientB, clientC))
    .register(Command.STATS_GLOBAL, new StatsGlobalHandler(clientA, clientB, clientC))
    .register(Command.PULL_MY_CHOICES, new PullMyChoicesHandler(clientA, clientB, clientC));
```

- [ ] **Step 3: 构建确认通过**

```bash
mvn -pl integration -am compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add integration/src/main/java/integration/server/IntegrationServer.java
git commit -m "feat(integration): register PullMyChoicesHandler"
```

---

## Task 11: unifiedMyChoiceToA.xsl + 单测

**Files:**
- Create: `college-a/src/main/resources/xsl/unifiedMyChoiceToA.xsl`
- Test: `college-a/src/test/java/college/a/xml/UnifiedMyChoiceToAXslTest.java`

- [ ] **Step 1: 写失败测试**

新建 `college-a/src/test/java/college/a/xml/UnifiedMyChoiceToAXslTest.java`：

```java
package college.a.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedMyChoiceToAXslTest {
  @Test void unified_to_a_local_keeps_origin_and_sno() {
    var t = XsltTransformer.fromClasspath("/xsl/unifiedMyChoiceToA.xsl");
    String in = "<classes><class origin=\"B\">"
        + "<id>BC003</id><name>OS</name><time>32</time><score>3</score>"
        + "<teacher>Wang</teacher><location>B201</location>"
        + "<share>Y</share><sno>AS001</sno><grade></grade>"
        + "</class></classes>";
    String out = t.transform(in);
    assertTrue(out.contains("<课程编号>BC003</课程编号>"));
    assertTrue(out.contains("<授课老师>Wang</授课老师>"));
    assertTrue(out.contains("<学生编号>AS001</学生编号>"));
    assertTrue(out.contains("origin=\"B\"") || out.contains("<来源>B</来源>"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-a test -Dtest=UnifiedMyChoiceToAXslTest
```

Expected: FAIL。

- [ ] **Step 3: 创建 XSL**

新建 `college-a/src/main/resources/xsl/unifiedMyChoiceToA.xsl`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="classes">
    <课程集>
      <xsl:for-each select="class">
        <课程>
          <来源><xsl:value-of select="@origin"/></来源>
          <课程编号><xsl:value-of select="id"/></课程编号>
          <课程名称><xsl:value-of select="name"/></课程名称>
          <课时><xsl:value-of select="time"/></课时>
          <学分><xsl:value-of select="score"/></学分>
          <授课老师><xsl:value-of select="teacher"/></授课老师>
          <授课地点><xsl:value-of select="location"/></授课地点>
          <学生编号><xsl:value-of select="sno"/></学生编号>
          <成绩><xsl:value-of select="grade"/></成绩>
        </课程>
      </xsl:for-each>
    </课程集>
  </xsl:template>
</xsl:stylesheet>
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-a test -Dtest=UnifiedMyChoiceToAXslTest
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add college-a/src/main/resources/xsl/unifiedMyChoiceToA.xsl \
        college-a/src/test/java/college/a/xml/UnifiedMyChoiceToAXslTest.java
git commit -m "feat(college-a): unifiedMyChoiceToA.xsl renders cross-college choices in A's fields"
```

---

## Task 12: unifiedMyChoiceToB.xsl + 单测

**Files:**
- Create: `college-b/src/main/resources/xsl/unifiedMyChoiceToB.xsl`
- Test: `college-b/src/test/java/college/b/xml/UnifiedMyChoiceToBXslTest.java`

- [ ] **Step 1: 写失败测试**

新建 `college-b/src/test/java/college/b/xml/UnifiedMyChoiceToBXslTest.java`：

```java
package college.b.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedMyChoiceToBXslTest {
  @Test void unified_to_b_local_keeps_origin_and_sno() {
    var t = XsltTransformer.fromClasspath("/xsl/unifiedMyChoiceToB.xsl");
    String in = "<classes><class origin=\"A\">"
        + "<id>AC001</id><name>Math</name><time>48</time><score>3</score>"
        + "<teacher>Zhao</teacher><location>A101</location>"
        + "<share>Y</share><sno>BS001</sno><grade></grade>"
        + "</class></classes>";
    String out = t.transform(in);
    assertTrue(out.contains("<编号>AC001</编号>"));
    assertTrue(out.contains("<老师>Zhao</老师>"));
    assertTrue(out.contains("<学号>BS001</学号>"));
    assertTrue(out.contains("<来源>A</来源>"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-b test -Dtest=UnifiedMyChoiceToBXslTest
```

Expected: FAIL。

- [ ] **Step 3: 创建 XSL**

新建 `college-b/src/main/resources/xsl/unifiedMyChoiceToB.xsl`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="classes">
    <课程集>
      <xsl:for-each select="class">
        <课程>
          <来源><xsl:value-of select="@origin"/></来源>
          <编号><xsl:value-of select="id"/></编号>
          <名称><xsl:value-of select="name"/></名称>
          <课时><xsl:value-of select="time"/></课时>
          <学分><xsl:value-of select="score"/></学分>
          <老师><xsl:value-of select="teacher"/></老师>
          <地点><xsl:value-of select="location"/></地点>
          <学号><xsl:value-of select="sno"/></学号>
          <得分><xsl:value-of select="grade"/></得分>
        </课程>
      </xsl:for-each>
    </课程集>
  </xsl:template>
</xsl:stylesheet>
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-b test -Dtest=UnifiedMyChoiceToBXslTest
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add college-b/src/main/resources/xsl/unifiedMyChoiceToB.xsl \
        college-b/src/test/java/college/b/xml/UnifiedMyChoiceToBXslTest.java
git commit -m "feat(college-b): unifiedMyChoiceToB.xsl renders cross-college choices in B's fields"
```

---

## Task 13: unifiedMyChoiceToC.xsl + 单测

**Files:**
- Create: `college-c/src/main/resources/xsl/unifiedMyChoiceToC.xsl`
- Test: `college-c/src/test/java/college/c/xml/UnifiedMyChoiceToCXslTest.java`

- [ ] **Step 1: 写失败测试**

新建 `college-c/src/test/java/college/c/xml/UnifiedMyChoiceToCXslTest.java`：

```java
package college.c.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedMyChoiceToCXslTest {
  @Test void unified_to_c_local_keeps_origin_and_sno() {
    var t = XsltTransformer.fromClasspath("/xsl/unifiedMyChoiceToC.xsl");
    String in = "<classes><class origin=\"A\">"
        + "<id>AC001</id><name>Math</name><time>48</time><score>3</score>"
        + "<teacher>Zhao</teacher><location>A101</location>"
        + "<share>Y</share><sno>CS001</sno><grade></grade>"
        + "</class></classes>";
    String out = t.transform(in);
    assertTrue(out.contains("<Cno>AC001</Cno>"));
    assertTrue(out.contains("<Tec>Zhao</Tec>"));
    assertTrue(out.contains("<Sno>CS001</Sno>"));
    assertTrue(out.contains("<Org>A</Org>"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-c test -Dtest=UnifiedMyChoiceToCXslTest
```

Expected: FAIL。

- [ ] **Step 3: 创建 XSL**

新建 `college-c/src/main/resources/xsl/unifiedMyChoiceToC.xsl`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="classes">
    <courses>
      <xsl:for-each select="class">
        <course>
          <Org><xsl:value-of select="@origin"/></Org>
          <Cno><xsl:value-of select="id"/></Cno>
          <Cnm><xsl:value-of select="name"/></Cnm>
          <Ctm><xsl:value-of select="time"/></Ctm>
          <Cpt><xsl:value-of select="score"/></Cpt>
          <Tec><xsl:value-of select="teacher"/></Tec>
          <Pla><xsl:value-of select="location"/></Pla>
          <Sno><xsl:value-of select="sno"/></Sno>
          <Grd><xsl:value-of select="grade"/></Grd>
        </course>
      </xsl:for-each>
    </courses>
  </xsl:template>
</xsl:stylesheet>
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-c test -Dtest=UnifiedMyChoiceToCXslTest
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add college-c/src/main/resources/xsl/unifiedMyChoiceToC.xsl \
        college-c/src/test/java/college/c/xml/UnifiedMyChoiceToCXslTest.java
git commit -m "feat(college-c): unifiedMyChoiceToC.xsl renders cross-college choices in C's fields"
```

---

## Task 14: college-a ListMyChoicesHandler + 单测

**Files:**
- Create: `college-a/src/main/java/college/a/server/handler/ListMyChoicesHandler.java`
- Test: `college-a/src/test/java/college/a/server/handler/ListMyChoicesHandlerTest.java`

- [ ] **Step 1: 写失败测试**

测试用真 `ServerSocket` 模拟 integration 端，验证「转发 + 拼装 + 失败兜底」3 个语义。新建 `college-a/src/test/java/college/a/server/handler/ListMyChoicesHandlerTest.java`：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.server.CollegeServerConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListMyChoicesHandlerTest {

  private static final String CROSS_RESULT_OK =
      "<crossEnrolledResult>"
      + "<classes><class origin=\"B\">"
      + "<id>BC003</id><name>OS</name><time>32</time><score>3</score>"
      + "<teacher>Wang</teacher><location>B201</location><share>Y</share>"
      + "<sno>AS001</sno><grade></grade></class></classes>"
      + "<errors/></crossEnrolledResult>";

  private ChoiceDao choiceDaoWith(String courseId) {
    var d = mock(ChoiceDao.class);
    when(d.findByStudent("AS001")).thenReturn(List.of(
        new ChoiceDao.Row(courseId, "AS001", null, "A")));
    return d;
  }

  private CourseDao courseDaoWith(String courseId, String name) {
    var d = mock(CourseDao.class);
    when(d.findById(courseId)).thenReturn(Optional.of(
        new CourseDao.Row(courseId, name, 48, BigDecimal.valueOf(3),
            "Zhao", "A101", false)));
    return d;
  }

  /** integration mock: accept one connection, reply with given Message. */
  private ServerSocket startMockIntegration(Message reply) throws Exception {
    var ss = new ServerSocket(0);
    new Thread(() -> {
      try (Socket sock = ss.accept()) {
        Message.read(sock.getInputStream()); // consume request
        Message.write(sock.getOutputStream(), reply);
      } catch (Exception ignored) {}
    }).start();
    return ss;
  }

  @Test
  void wraps_local_and_cross_choices_into_myChoices() throws Exception {
    var choiceDao = choiceDaoWith("AC001");
    var courseDao = courseDaoWith("AC001", "Math");
    try (var integ = startMockIntegration(Message.ok("r", CROSS_RESULT_OK))) {
      var config = new CollegeServerConfig("A", "AC", "AS");
      // override integration port via system property
      System.setProperty("integration.port", Integer.toString(integ.getLocalPort()));
      var configForTest = new CollegeServerConfig("A", "AC", "AS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, configForTest);

      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>AS001</sno>"));

      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<myChoices sno=\"AS001\" home=\"A\">"));
      assertTrue(res.payload().contains("<home>"));
      assertTrue(res.payload().contains("<课程编号>AC001</课程编号>"));
      assertTrue(res.payload().contains("<crossEnrolled>"));
      assertTrue(res.payload().contains("<class origin=\"B\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void integration_unreachable_keeps_home_block_and_records_error() {
    var choiceDao = choiceDaoWith("AC001");
    var courseDao = courseDaoWith("AC001", "Math");
    System.setProperty("integration.port", "1");  // surely refused
    try {
      var config = new CollegeServerConfig("A", "AC", "AS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);

      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>AS001</sno>"));

      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<课程编号>AC001</课程编号>"));
      assertTrue(res.payload().contains("<error college=\"*\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void local_dao_failure_returns_err() {
    var choiceDao = mock(ChoiceDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db"));
    var courseDao = mock(CourseDao.class);
    var config = new CollegeServerConfig("A", "AC", "AS");
    var h = new ListMyChoicesHandler(choiceDao, courseDao, config);

    var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>AS001</sno>"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }

  @Test
  void bad_payload_returns_err() {
    var h = new ListMyChoicesHandler(mock(ChoiceDao.class), mock(CourseDao.class),
        new CollegeServerConfig("A", "AC", "AS"));
    var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "not xml"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("BAD_PAYLOAD"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-a test -Dtest=ListMyChoicesHandlerTest
```

Expected: 编译失败。

- [ ] **Step 3: 实现**

新建 `college-a/src/main/java/college/a/server/handler/ListMyChoicesHandler.java`：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.server.CollegeServerConfig;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.net.Socket;
import java.util.UUID;

public class ListMyChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CourseDao courseDao;
  private final CollegeServerConfig config;

  public ListMyChoicesHandler(ChoiceDao choiceDao, CourseDao courseDao,
                              CollegeServerConfig config) {
    this.choiceDao = choiceDao;
    this.courseDao = courseDao;
    this.config = config;
  }

  @Override
  public Message handle(Message req) {
    String sno;
    try {
      sno = XmlIO.parse(req.payload()).getRootElement().getText().trim();
      if (sno.isEmpty()) {
        return Message.err(req.requestId(), "BAD_PAYLOAD", "empty sno");
      }
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }

    Document out = DocumentHelper.createDocument();
    Element root = out.addElement("myChoices")
        .addAttribute("sno", sno)
        .addAttribute("home", config.collegeCode);
    Element home = root.addElement("home");
    Element crossEnrolled = root.addElement("crossEnrolled");
    Element errors = root.addElement("errors");

    // 1) Local join: build <home><课程集>...</课程集></home>
    try {
      Element courseSet = home.addElement("课程集");
      var rows = choiceDao.findByStudent(sno);
      for (var ch : rows) {
        var courseOpt = courseDao.findById(ch.courseId());
        if (courseOpt.isEmpty()) continue;
        var c = courseOpt.get();
        Element entry = courseSet.addElement("课程");
        entry.addElement("课程编号").setText(c.id());
        entry.addElement("课程名称").setText(c.name());
        entry.addElement("课时").setText(Integer.toString(c.hours()));
        entry.addElement("学分").setText(c.score().toPlainString());
        entry.addElement("授课老师").setText(c.teacher());
        entry.addElement("授课地点").setText(c.location());
        entry.addElement("学生编号").setText(ch.studentId());
        entry.addElement("成绩").setText(ch.score() == null ? "" : ch.score());
      }
    } catch (RuntimeException e) {
      return Message.err(req.requestId(), "LOCAL_QUERY_FAILED", e.getMessage());
    }

    // 2) Forward to integration for cross-college part.
    String pullPayload = "<myChoicesReq sno=\"" + sno + "\" home=\""
        + config.collegeCode + "\"/>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.PULL_MY_CHOICES, UUID.randomUUID().toString(), pullPayload));
      Message resp = Message.read(sock.getInputStream());
      if (resp.command() == Command.OK) {
        Element pulled = XmlIO.parse(resp.payload()).getRootElement();
        Element pulledClasses = pulled.element("classes");
        if (pulledClasses != null) crossEnrolled.add(pulledClasses.createCopy());
        Element pulledErrors = pulled.element("errors");
        if (pulledErrors != null) {
          for (Object o : pulledErrors.elements("error")) {
            errors.add(((Element) o).createCopy());
          }
        }
      } else {
        addErrorAll(errors, "integration ERR: " + resp.payload());
      }
    } catch (Exception e) {
      addErrorAll(errors, "integration unreachable: " + e.getMessage());
    }

    return Message.ok(req.requestId(), XmlIO.toPrettyString(out));
  }

  private static void addErrorAll(Element errors, String detail) {
    Element err = errors.addElement("error").addAttribute("college", "*");
    err.setText(detail);
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-a test -Dtest=ListMyChoicesHandlerTest
```

Expected: PASS, 4/4。

- [ ] **Step 5: 提交**

```bash
git add college-a/src/main/java/college/a/server/handler/ListMyChoicesHandler.java \
        college-a/src/test/java/college/a/server/handler/ListMyChoicesHandlerTest.java
git commit -m "feat(college-a): ListMyChoicesHandler joins local + forwards PULL_MY_CHOICES"
```

---

## Task 15: college-b ListMyChoicesHandler + 单测

**Files:**
- Create: `college-b/src/main/java/college/b/server/handler/ListMyChoicesHandler.java`
- Test: `college-b/src/test/java/college/b/server/handler/ListMyChoicesHandlerTest.java`

- [ ] **Step 1: 写失败测试**

新建 `college-b/src/test/java/college/b/server/handler/ListMyChoicesHandlerTest.java`：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import college.b.server.CollegeServerConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListMyChoicesHandlerTest {

  private static final String CROSS_RESULT_OK =
      "<crossEnrolledResult>"
      + "<classes><class origin=\"A\">"
      + "<id>AC001</id><name>Math</name><time>48</time><score>3</score>"
      + "<teacher>Zhao</teacher><location>A101</location><share>Y</share>"
      + "<sno>BS001</sno><grade></grade></class></classes>"
      + "<errors/></crossEnrolledResult>";

  private ChoiceDao choiceDaoWith(String courseId) {
    var d = mock(ChoiceDao.class);
    when(d.findByStudent("BS001")).thenReturn(List.of(
        new ChoiceDao.Row(courseId, "BS001", null)));
    return d;
  }

  private CourseDao courseDaoWith(String courseId) {
    var d = mock(CourseDao.class);
    when(d.findById(courseId)).thenReturn(Optional.of(
        new CourseDao.Row(courseId, "DB", 32, BigDecimal.valueOf(3),
            "Li", "B101", false)));
    return d;
  }

  private ServerSocket startMockIntegration(Message reply) throws Exception {
    var ss = new ServerSocket(0);
    new Thread(() -> {
      try (Socket sock = ss.accept()) {
        Message.read(sock.getInputStream());
        Message.write(sock.getOutputStream(), reply);
      } catch (Exception ignored) {}
    }).start();
    return ss;
  }

  @Test
  void wraps_local_and_cross_choices_into_myChoices() throws Exception {
    var choiceDao = choiceDaoWith("BC001");
    var courseDao = courseDaoWith("BC001");
    try (var integ = startMockIntegration(Message.ok("r", CROSS_RESULT_OK))) {
      System.setProperty("integration.port", Integer.toString(integ.getLocalPort()));
      var config = new CollegeServerConfig("B", "BC", "BS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);

      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>BS001</sno>"));

      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<myChoices sno=\"BS001\" home=\"B\">"));
      assertTrue(res.payload().contains("<编号>BC001</编号>"));
      assertTrue(res.payload().contains("<class origin=\"A\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void integration_unreachable_keeps_home_block_and_records_error() {
    var choiceDao = choiceDaoWith("BC001");
    var courseDao = courseDaoWith("BC001");
    System.setProperty("integration.port", "1");
    try {
      var config = new CollegeServerConfig("B", "BC", "BS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);
      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>BS001</sno>"));
      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<编号>BC001</编号>"));
      assertTrue(res.payload().contains("<error college=\"*\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void local_dao_failure_returns_err() {
    var choiceDao = mock(ChoiceDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db"));
    var h = new ListMyChoicesHandler(choiceDao, mock(CourseDao.class),
        new CollegeServerConfig("B", "BC", "BS"));
    var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>BS001</sno>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-b test -Dtest=ListMyChoicesHandlerTest
```

Expected: 编译失败。

- [ ] **Step 3: 实现**

新建 `college-b/src/main/java/college/b/server/handler/ListMyChoicesHandler.java`：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import college.b.server.CollegeServerConfig;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.net.Socket;
import java.util.UUID;

public class ListMyChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CourseDao courseDao;
  private final CollegeServerConfig config;

  public ListMyChoicesHandler(ChoiceDao choiceDao, CourseDao courseDao,
                              CollegeServerConfig config) {
    this.choiceDao = choiceDao;
    this.courseDao = courseDao;
    this.config = config;
  }

  @Override
  public Message handle(Message req) {
    String sno;
    try {
      sno = XmlIO.parse(req.payload()).getRootElement().getText().trim();
      if (sno.isEmpty()) return Message.err(req.requestId(), "BAD_PAYLOAD", "empty sno");
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }

    Document out = DocumentHelper.createDocument();
    Element root = out.addElement("myChoices")
        .addAttribute("sno", sno)
        .addAttribute("home", config.collegeCode);
    Element home = root.addElement("home");
    Element crossEnrolled = root.addElement("crossEnrolled");
    Element errors = root.addElement("errors");

    try {
      Element courseSet = home.addElement("课程集");
      var rows = choiceDao.findByStudent(sno);
      for (var ch : rows) {
        var courseOpt = courseDao.findById(ch.courseId());
        if (courseOpt.isEmpty()) continue;
        var c = courseOpt.get();
        Element entry = courseSet.addElement("课程");
        entry.addElement("编号").setText(c.id());
        entry.addElement("名称").setText(c.name());
        entry.addElement("课时").setText(Integer.toString(c.hours()));
        entry.addElement("学分").setText(c.score().toPlainString());
        entry.addElement("老师").setText(c.teacher());
        entry.addElement("地点").setText(c.location());
        entry.addElement("学号").setText(ch.studentId());
        entry.addElement("得分").setText(ch.score() == null ? "" : ch.score());
      }
    } catch (RuntimeException e) {
      return Message.err(req.requestId(), "LOCAL_QUERY_FAILED", e.getMessage());
    }

    String pullPayload = "<myChoicesReq sno=\"" + sno + "\" home=\""
        + config.collegeCode + "\"/>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.PULL_MY_CHOICES, UUID.randomUUID().toString(), pullPayload));
      Message resp = Message.read(sock.getInputStream());
      if (resp.command() == Command.OK) {
        Element pulled = XmlIO.parse(resp.payload()).getRootElement();
        Element pulledClasses = pulled.element("classes");
        if (pulledClasses != null) crossEnrolled.add(pulledClasses.createCopy());
        Element pulledErrors = pulled.element("errors");
        if (pulledErrors != null) {
          for (Object o : pulledErrors.elements("error")) {
            errors.add(((Element) o).createCopy());
          }
        }
      } else {
        addErrorAll(errors, "integration ERR: " + resp.payload());
      }
    } catch (Exception e) {
      addErrorAll(errors, "integration unreachable: " + e.getMessage());
    }

    return Message.ok(req.requestId(), XmlIO.toPrettyString(out));
  }

  private static void addErrorAll(Element errors, String detail) {
    Element err = errors.addElement("error").addAttribute("college", "*");
    err.setText(detail);
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-b test -Dtest=ListMyChoicesHandlerTest
```

Expected: PASS, 3/3。

- [ ] **Step 5: 提交**

```bash
git add college-b/src/main/java/college/b/server/handler/ListMyChoicesHandler.java \
        college-b/src/test/java/college/b/server/handler/ListMyChoicesHandlerTest.java
git commit -m "feat(college-b): ListMyChoicesHandler joins local + forwards PULL_MY_CHOICES"
```

---

## Task 16: college-c ListMyChoicesHandler + 单测

**Files:**
- Create: `college-c/src/main/java/college/c/server/handler/ListMyChoicesHandler.java`
- Test: `college-c/src/test/java/college/c/server/handler/ListMyChoicesHandlerTest.java`

- [ ] **Step 1: 写失败测试**

新建 `college-c/src/test/java/college/c/server/handler/ListMyChoicesHandlerTest.java`：

```java
package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import college.c.server.CollegeServerConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListMyChoicesHandlerTest {

  private static final String CROSS_RESULT_OK =
      "<crossEnrolledResult>"
      + "<classes><class origin=\"A\">"
      + "<id>AC001</id><name>Math</name><time>48</time><score>3</score>"
      + "<teacher>Zhao</teacher><location>A101</location><share>Y</share>"
      + "<sno>CS001</sno><grade></grade></class></classes>"
      + "<errors/></crossEnrolledResult>";

  private ChoiceDao choiceDaoWith(String courseId) {
    var d = mock(ChoiceDao.class);
    when(d.findByStudent("CS001")).thenReturn(List.of(
        new ChoiceDao.Row(courseId, "CS001", null)));
    return d;
  }

  private CourseDao courseDaoWith(String courseId) {
    var d = mock(CourseDao.class);
    when(d.findById(courseId)).thenReturn(Optional.of(
        new CourseDao.Row(courseId, "Net", 32, BigDecimal.valueOf(2),
            "Wang", "C301", false)));
    return d;
  }

  private ServerSocket startMockIntegration(Message reply) throws Exception {
    var ss = new ServerSocket(0);
    new Thread(() -> {
      try (Socket sock = ss.accept()) {
        Message.read(sock.getInputStream());
        Message.write(sock.getOutputStream(), reply);
      } catch (Exception ignored) {}
    }).start();
    return ss;
  }

  @Test
  void wraps_local_and_cross_choices_into_myChoices() throws Exception {
    var choiceDao = choiceDaoWith("CC001");
    var courseDao = courseDaoWith("CC001");
    try (var integ = startMockIntegration(Message.ok("r", CROSS_RESULT_OK))) {
      System.setProperty("integration.port", Integer.toString(integ.getLocalPort()));
      var config = new CollegeServerConfig("C", "CC", "CS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);
      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>CS001</sno>"));
      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<myChoices sno=\"CS001\" home=\"C\">"));
      assertTrue(res.payload().contains("<Cno>CC001</Cno>"));
      assertTrue(res.payload().contains("<class origin=\"A\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void integration_unreachable_keeps_home_block_and_records_error() {
    var choiceDao = choiceDaoWith("CC001");
    var courseDao = courseDaoWith("CC001");
    System.setProperty("integration.port", "1");
    try {
      var config = new CollegeServerConfig("C", "CC", "CS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);
      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>CS001</sno>"));
      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<Cno>CC001</Cno>"));
      assertTrue(res.payload().contains("<error college=\"*\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
mvn -pl college-c test -Dtest=ListMyChoicesHandlerTest
```

Expected: 编译失败。

- [ ] **Step 3: 实现**

新建 `college-c/src/main/java/college/c/server/handler/ListMyChoicesHandler.java`：

```java
package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import college.c.server.CollegeServerConfig;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.net.Socket;
import java.util.UUID;

public class ListMyChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CourseDao courseDao;
  private final CollegeServerConfig config;

  public ListMyChoicesHandler(ChoiceDao choiceDao, CourseDao courseDao,
                              CollegeServerConfig config) {
    this.choiceDao = choiceDao;
    this.courseDao = courseDao;
    this.config = config;
  }

  @Override
  public Message handle(Message req) {
    String sno;
    try {
      sno = XmlIO.parse(req.payload()).getRootElement().getText().trim();
      if (sno.isEmpty()) return Message.err(req.requestId(), "BAD_PAYLOAD", "empty sno");
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }

    Document out = DocumentHelper.createDocument();
    Element root = out.addElement("myChoices")
        .addAttribute("sno", sno)
        .addAttribute("home", config.collegeCode);
    Element home = root.addElement("home");
    Element crossEnrolled = root.addElement("crossEnrolled");
    Element errors = root.addElement("errors");

    try {
      Element courses = home.addElement("courses");
      var rows = choiceDao.findByStudent(sno);
      for (var ch : rows) {
        var courseOpt = courseDao.findById(ch.courseId());
        if (courseOpt.isEmpty()) continue;
        var c = courseOpt.get();
        Element entry = courses.addElement("course");
        entry.addElement("Cno").setText(c.id());
        entry.addElement("Cnm").setText(c.name());
        entry.addElement("Ctm").setText(Integer.toString(c.hours()));
        entry.addElement("Cpt").setText(c.score().toPlainString());
        entry.addElement("Tec").setText(c.teacher());
        entry.addElement("Pla").setText(c.location());
        entry.addElement("Sno").setText(ch.studentId());
        entry.addElement("Grd").setText(ch.grade() == null ? "" : ch.grade());
      }
    } catch (RuntimeException e) {
      return Message.err(req.requestId(), "LOCAL_QUERY_FAILED", e.getMessage());
    }

    String pullPayload = "<myChoicesReq sno=\"" + sno + "\" home=\""
        + config.collegeCode + "\"/>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.PULL_MY_CHOICES, UUID.randomUUID().toString(), pullPayload));
      Message resp = Message.read(sock.getInputStream());
      if (resp.command() == Command.OK) {
        Element pulled = XmlIO.parse(resp.payload()).getRootElement();
        Element pulledClasses = pulled.element("classes");
        if (pulledClasses != null) crossEnrolled.add(pulledClasses.createCopy());
        Element pulledErrors = pulled.element("errors");
        if (pulledErrors != null) {
          for (Object o : pulledErrors.elements("error")) {
            errors.add(((Element) o).createCopy());
          }
        }
      } else {
        addErrorAll(errors, "integration ERR: " + resp.payload());
      }
    } catch (Exception e) {
      addErrorAll(errors, "integration unreachable: " + e.getMessage());
    }

    return Message.ok(req.requestId(), XmlIO.toPrettyString(out));
  }

  private static void addErrorAll(Element errors, String detail) {
    Element err = errors.addElement("error").addAttribute("college", "*");
    err.setText(detail);
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn -pl college-c test -Dtest=ListMyChoicesHandlerTest
```

Expected: PASS, 2/2。

- [ ] **Step 5: 提交**

```bash
git add college-c/src/main/java/college/c/server/handler/ListMyChoicesHandler.java \
        college-c/src/test/java/college/c/server/handler/ListMyChoicesHandlerTest.java
git commit -m "feat(college-c): ListMyChoicesHandler joins local + forwards PULL_MY_CHOICES"
```

---

## Task 17: 三院 server 注册新 handler

**Files:**
- Modify: `college-a/src/main/java/college/a/server/CollegeAServer.java`
- Modify: `college-b/src/main/java/college/b/server/CollegeBServer.java`
- Modify: `college-c/src/main/java/college/c/server/CollegeCServer.java`

- [ ] **Step 1: A 院注册**

在 `CollegeAServer.java` 的 `import` 段追加：

```java
import college.a.server.handler.AskMyChoicesHandler;
import college.a.server.handler.ListMyChoicesHandler;
```

在 `main()` 的 router 链上追加（与现有 `STATS_PULL` 注册同级）：

```java
.register(Command.LIST_MY_CHOICES, new ListMyChoicesHandler(choiceDao, courseDao, config))
.register(Command.ASK_MY_CHOICES, new AskMyChoicesHandler(choiceDao, courseDao));
```

- [ ] **Step 2: B 院注册**

在 `CollegeBServer.java` import 段追加：

```java
import college.b.server.handler.AskMyChoicesHandler;
import college.b.server.handler.ListMyChoicesHandler;
```

router 链追加：

```java
.register(Command.LIST_MY_CHOICES, new ListMyChoicesHandler(choiceDao, courseDao, config))
.register(Command.ASK_MY_CHOICES, new AskMyChoicesHandler(choiceDao, courseDao));
```

- [ ] **Step 3: C 院注册**

在 `CollegeCServer.java` import 段追加：

```java
import college.c.server.handler.AskMyChoicesHandler;
import college.c.server.handler.ListMyChoicesHandler;
```

router 链追加：

```java
.register(Command.LIST_MY_CHOICES, new ListMyChoicesHandler(choiceDao, courseDao, config))
.register(Command.ASK_MY_CHOICES, new AskMyChoicesHandler(choiceDao, courseDao));
```

- [ ] **Step 4: 全模块构建并跑测试**

```bash
mvn -DskipTests install
mvn test
```

Expected: BUILD SUCCESS，所有测试通过。

- [ ] **Step 5: 提交**

```bash
git add college-a/src/main/java/college/a/server/CollegeAServer.java \
        college-b/src/main/java/college/b/server/CollegeBServer.java \
        college-c/src/main/java/college/c/server/CollegeCServer.java
git commit -m "feat(servers): register LIST_MY_CHOICES and ASK_MY_CHOICES handlers"
```

---

## Task 18: 客户端 UI — MyChoicesDialog + CourseListFrame 按钮

**Files:**
- Create: `client/src/main/java/client/ui/MyChoicesDialog.java`
- Modify: `client/src/main/java/client/ui/CourseListFrame.java`

无单测：项目惯例 UI 不写单测。

- [ ] **Step 1: 实现 MyChoicesDialog**

新建 `client/src/main/java/client/ui/MyChoicesDialog.java`：

```java
package client.ui;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsltTransformer;
import client.net.CollegeClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

public class MyChoicesDialog extends JDialog {

  private static final String[] COLS_HOME =
      {"课程编号/编号/Cno", "课程名称", "学分", "授课老师", "授课地点", "成绩"};
  private static final String[] COLS_CROSS =
      {"来自学院", "课程编号/编号/Cno", "课程名称", "学分", "授课老师", "授课地点", "成绩"};

  public MyChoicesDialog(JFrame parent, String college, String sno, CollegeClient client) {
    super(parent, "我的选课 - " + sno, true);
    setSize(820, 520);
    setLocationRelativeTo(parent);

    var status = new JLabel("正在加载...", SwingConstants.CENTER);
    var homeModel = new DefaultTableModel(COLS_HOME, 0) {
      @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    var crossModel = new DefaultTableModel(COLS_CROSS, 0) {
      @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
        wrap("本院选课", new JTable(homeModel)),
        wrap("跨院选课", new JTable(crossModel)));
    split.setResizeWeight(0.5);

    var panel = new JPanel(new BorderLayout());
    panel.add(split, BorderLayout.CENTER);
    panel.add(status, BorderLayout.SOUTH);
    add(panel);

    new Thread(() -> {
      try {
        Message res = client.send(new Message(Command.LIST_MY_CHOICES,
            UUID.randomUUID().toString(), "<sno>" + esc(sno) + "</sno>"));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            populate(college, res.payload(), homeModel, crossModel, status);
          } else {
            status.setText("加载失败: " + res.payload());
          }
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> status.setText("网络错误: " + e.getMessage()));
      }
    }).start();
  }

  private static JComponent wrap(String title, JTable table) {
    var sp = new JScrollPane(table);
    sp.setBorder(BorderFactory.createTitledBorder(title));
    return sp;
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static void populate(String college, String xml,
                               DefaultTableModel homeModel, DefaultTableModel crossModel,
                               JLabel status) {
    try {
      var root = XmlIO.parse(xml).getRootElement();
      // home block
      var homeEl = root.element("home");
      if (homeEl != null) {
        if ("C".equals(college)) {
          var courses = homeEl.element("courses");
          if (courses != null) {
            for (Object o : courses.elements("course")) {
              var e = (org.dom4j.Element) o;
              homeModel.addRow(new Object[]{
                  e.elementText("Cno"), e.elementText("Cnm"),
                  e.elementText("Cpt"), e.elementText("Tec"),
                  e.elementText("Pla"), e.elementText("Grd")
              });
            }
          }
        } else {
          var courseSet = homeEl.element("课程集");
          if (courseSet != null) {
            for (Object o : courseSet.elements("课程")) {
              var e = (org.dom4j.Element) o;
              boolean isB = "B".equals(college);
              homeModel.addRow(new Object[]{
                  e.elementText(isB ? "编号" : "课程编号"),
                  e.elementText(isB ? "名称" : "课程名称"),
                  e.elementText("学分"),
                  e.elementText(isB ? "老师" : "授课老师"),
                  e.elementText(isB ? "地点" : "授课地点"),
                  e.elementText(isB ? "得分" : "成绩")
              });
            }
          }
        }
      }
      // cross block: feed <classes> through unifiedMyChoiceToX.xsl
      var crossEl = root.element("crossEnrolled");
      if (crossEl != null) {
        var classes = crossEl.element("classes");
        if (classes != null && !classes.elements().isEmpty()) {
          String unifiedXml = classes.asXML();
          String xsl = "/xsl/unifiedMyChoiceTo" + college + ".xsl";
          String localized = XsltTransformer.fromClasspath(xsl).transform(unifiedXml);
          var localRoot = XmlIO.parse(localized).getRootElement();
          if ("C".equals(college)) {
            for (Object o : localRoot.elements("course")) {
              var e = (org.dom4j.Element) o;
              crossModel.addRow(new Object[]{
                  e.elementText("Org"),
                  e.elementText("Cno"), e.elementText("Cnm"),
                  e.elementText("Cpt"), e.elementText("Tec"),
                  e.elementText("Pla"), e.elementText("Grd")
              });
            }
          } else {
            boolean isB = "B".equals(college);
            for (Object o : localRoot.elements("课程")) {
              var e = (org.dom4j.Element) o;
              crossModel.addRow(new Object[]{
                  e.elementText("来源"),
                  e.elementText(isB ? "编号" : "课程编号"),
                  e.elementText(isB ? "名称" : "课程名称"),
                  e.elementText("学分"),
                  e.elementText(isB ? "老师" : "授课老师"),
                  e.elementText(isB ? "地点" : "授课地点"),
                  e.elementText(isB ? "得分" : "成绩")
              });
            }
          }
        }
      }
      // errors
      var errors = root.element("errors");
      if (errors != null && !errors.elements().isEmpty()) {
        var sb = new StringBuilder("外院故障: ");
        for (Object o : errors.elements("error")) {
          var e = (org.dom4j.Element) o;
          sb.append("[").append(e.attributeValue("college")).append("] ")
            .append(e.getText()).append("  ");
        }
        status.setText(sb.toString());
        status.setForeground(Color.RED);
      } else {
        int total = homeModel.getRowCount() + crossModel.getRowCount();
        status.setText(total == 0 ? "该学生无任何选课记录" : "加载完成 (共 " + total + " 门)");
      }
    } catch (Exception e) {
      status.setText("解析失败: " + e.getMessage());
      status.setForeground(Color.RED);
    }
  }
}
```

- [ ] **Step 2: CourseListFrame 加按钮**

`CourseListFrame.java` 的字段段（与 `statsButton` 同处）追加：

```java
  private final JButton myChoicesButton;
```

构造器中按钮初始化与绑定段（找到 `statsButton = new JButton("全局统计");` 那段，在它下面追加）：

```java
    myChoicesButton = new JButton("我的选课");
    myChoicesButton.addActionListener(e -> openMyChoices());
```

`buttonPanel.add(statsButton);` 之后追加：

```java
    buttonPanel.add(myChoicesButton);
```

`setButtonsEnabled` 方法内追加：

```java
    myChoicesButton.setEnabled(enabled);
```

类内任意位置（建议放 `doStats()` 后）添加方法：

```java
  private void openMyChoices() {
    String input = JOptionPane.showInputDialog(this, "学生编号:", username);
    if (input == null || input.isBlank()) return;
    new MyChoicesDialog(this, college, input.trim(), client).setVisible(true);
  }
```

- [ ] **Step 3: 构建并启 GUI 验证**

```bash
mvn -pl client -am -DskipTests install
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: 全套测试**

```bash
mvn test
```

Expected: BUILD SUCCESS，所有现有测试 + 新加测试全绿。

- [ ] **Step 5: 提交**

```bash
git add client/src/main/java/client/ui/MyChoicesDialog.java \
        client/src/main/java/client/ui/CourseListFrame.java
git commit -m "feat(client): My Choices dialog with home + cross-college tables"
```

---

## 完成校验

最后做一遍冒烟：

```bash
./scripts/stop-all.sh
./scripts/start-all.sh
# 启 A 院 client：login as001/pwd001
# 点「我的选课」→ 输入 AS001 → 验证弹窗：
#   - 本院选课表填本院字段
#   - 跨院选课表（如有跨院选课）填外院字段 + 来源列
#   - 底部 status 显示「加载完成 (共 N 门)」或外院故障
```

总计：18 任务，~14 个新文件、6 处修改、~21 个新测试用例。


