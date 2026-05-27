# Plan 3: 跨院流程 + 课程共享 + 全局统计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Integration Server 馆中央路由和格式转换逻辑，打通三院的课程共享、跨院选课、跨院退课、全局统计全流程，更新客户端支持跨院视图。

**Architecture:** Integration Server 作为星型中心，通过 `CollegeClient`（临时 TCP 连接）与各院 Server 通信，应用 XSL 格式转换 + XSD 验证形成"数据交换关卡"。College Server 通过课程编号前缀（AC/BC/CC）判断本院/跨院，跨院请求委托给 Integration Server。SELECT 缓存共享课程到本地 DB，写操作遵循"真相源优先 + 补偿"策略。

**Tech Stack:** Java 17, Socket, DOM4J, Xerces, XSLT/TrAX, Swing。复用已有 Common 协议层和 10 个 XSL 文件。

**前置条件:** Plan 1+2 全部完成；三院 Server (A:9001, B:9002, C:9003) 和 Integration Server (9100) 可正常启动；三院种子数据已灌入。

**参考:** 设计 spec §§6.1-6.4（核心流程）、§7.1-7.3（错误处理与一致性）。

---

## Task 1: Integration Server 的 CollegeClient — 连接各院 Server

**Files:**
- Create: `integration/src/main/java/integration/net/CollegeClient.java`
- Create: `integration/src/test/java/integration/net/CollegeClientTest.java`

Integration Server 需要作为客户端连接各院 Server。与 `client` 模块的 CollegeClient 完全相同的模式，但作为 integration 模块内部工具。

- [ ] **Step 1: 写测试（FAIL）**

```java
// integration/src/test/java/integration/net/CollegeClientTest.java
package integration.net;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import static org.junit.jupiter.api.Assertions.*;

class CollegeClientTest {
  @Test
  void send_returns_server_response() throws Exception {
    try (var server = new ServerSocket(0)) {
      new Thread(() -> {
        try (var s = server.accept()) {
          Message req = Message.read(s.getInputStream());
          Message.write(s.getOutputStream(), Message.ok(req.requestId(), "<echo/>"));
        } catch (Exception ignore) {}
      }).start();

      var client = new CollegeClient("127.0.0.1", server.getLocalPort());
      Message res = client.send(new Message(Command.ASK_COURSE_INFO, "r1", ""));
      assertEquals(Command.OK, res.command());
    }
  }
}
```

Run: `mvn -pl integration -am test -Dtest=CollegeClientTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (CollegeClient not found).

- [ ] **Step 2: 实现 CollegeClient**

```java
// integration/src/main/java/integration/net/CollegeClient.java
package integration.net;

import cn.edu.di.protocol.Message;
import java.io.IOException;
import java.net.Socket;

public class CollegeClient {
  private final String host;
  private final int port;

  public CollegeClient(String host, int port) { this.host = host; this.port = port; }

  public Message send(Message req) throws IOException {
    try (var sock = new Socket(host, port)) {
      Message.write(sock.getOutputStream(), req);
      return Message.read(sock.getInputStream());
    }
  }
}
```

- [ ] **Step 3: PASS + commit**

```bash
mvn -pl integration -am test -Dtest=CollegeClientTest -Dsurefire.failIfNoSpecifiedTests=false
git add integration/src/
git commit -m "feat(integration): college client for cross-server communication"
```

---

## Task 2: College Server 的 IntegrationClient + 跨院路由基础设施

**Files:**
- Create: `college-a/src/main/java/college/a/server/CollegeServerConfig.java`
- Modify: `college-a/src/main/java/college/a/server/CollegeAServer.java`
- Modify: `college-b/src/main/java/college/b/server/CollegeBServer.java`（同理）
- Modify: `college-c/src/main/java/college/c/server/CollegeCServer.java`（同理）

每个 College Server 需要知道 Integration Server 的地址，以便跨院请求转发。通过系统属性 `integration.host` 和 `integration.port` 配置。

- [ ] **Step 1: 创建 CollegeServerConfig（A 院）**

```java
// college-a/src/main/java/college/a/server/CollegeServerConfig.java
package college.a.server;

// 注：college 模块不依赖 integration 模块。CollegeServerConfig 只持有
// integration host/port 字符串，跨院转发由后续任务用普通 Socket 完成。

public class CollegeServerConfig {
  public final String collegeCode;      // "A", "B", or "C"
  public final String integrationHost;
  public final int integrationPort;
  public final String courseIdPrefix;   // "AC", "BC", or "CC"
  public final String studentIdPrefix;  // "AS", "BS", or "CS"

  public CollegeServerConfig(String collegeCode, String courseIdPrefix, String studentIdPrefix) {
    this.collegeCode = collegeCode;
    this.courseIdPrefix = courseIdPrefix;
    this.studentIdPrefix = studentIdPrefix;
    this.integrationHost = System.getProperty("integration.host", "127.0.0.1");
    this.integrationPort = Integer.parseInt(System.getProperty("integration.port", "9100"));
  }

  /** 判断课程编号是否属于本院 */
  public boolean isLocalCourse(String courseId) {
    return courseId != null && courseId.startsWith(courseIdPrefix);
  }
}
```

- [ ] **Step 2: 在 CollegeAServer.main 中初始化 config**

修改 `CollegeAServer.main()`，在创建 DAO 前加入：

```java
var config = new CollegeServerConfig("A", "AC", "AS");
```

并将 config 传给后续 handler（Task 3-5 会用到）。

- [ ] **Step 3: B/C 院同步添加类似的 CollegeServerConfig**

B: `collegeCode="B"`, `courseIdPrefix="BC"`, `studentIdPrefix="BS"`
C: `collegeCode="C"`, `courseIdPrefix="CC"`, `studentIdPrefix="CS"`

- [ ] **Step 4: 编译验证 + commit**

```bash
mvn -q -DskipTests compile
git add college-a/src/ college-b/src/ college-c/src/
git commit -m "feat(college): server config with integration server address and id prefixes"
```

---

## Task 3: Integration Server 侧 — ASK_COURSE_INFO / SEND_COURSE_INFO handler

**Files:**
- Create: `integration/src/main/java/integration/server/handler/FetchSharedCoursesHandler.java`
- Test: `integration/src/test/java/integration/server/handler/FetchSharedCoursesHandlerTest.java`

Integration Server 收到 `FETCH_SHARED_COURSES` 后：向 B 和 C 各发 `ASK_COURSE_INFO`，收集各院的 `SEND_COURSE_INFO` 响应，用 XSL 统一格式转换后合并，再转回 A 格式返回。

先实现一个简化版：`FetchSharedCoursesHandler` 硬编码向所有非请求方的 College Server 拉课程，应用 formatX.xsl 收集统一格式课程。

- [ ] **Step 1: 写测试（mock CollegeClient）**

```java
// integration/src/test/java/integration/server/handler/FetchSharedCoursesHandlerTest.java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;

class FetchSharedCoursesHandlerTest {
  @Test
  void fetches_from_other_colleges() throws IOException {
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    when(clientB.send(any())).thenReturn(Message.ok("r1",
        "<课程集><课程><编号>BC001</编号><名称>DB</名称><课时>32</课时><学分>3</学分><老师>Li</老师><地点>B101</地点><共享>Y</共享></课程></课程集>"));
    when(clientC.send(any())).thenReturn(Message.ok("r2",
        "<courses><course><Cno>CC01</Cno><Cnm>Net</Cnm><Ctm>32</Ctm><Cpt>2</Cpt><Tec>Wang</Tec><Pla>C301</Pla><Share>Y</Share></course></courses>"));

    var handler = new FetchSharedCoursesHandler(clientB, clientC);
    var res = handler.handle(new Message(Command.FETCH_SHARED_COURSES, "r0", "<from>A</from>"));
    assertEquals(Command.OK, res.command());
    // Response should contain shared courses in A format (after XSL conversion)
    // At minimum contains the original payloads
  }
}
```

- [ ] **Step 2: 实现 FetchSharedCoursesHandler**

```java
// integration/src/main/java/integration/server/handler/FetchSharedCoursesHandler.java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsltTransformer;
import cn.edu.di.xml.XsdValidator;
import integration.net.CollegeClient;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.IOException;
import java.util.UUID;

public class FetchSharedCoursesHandler implements Handler {

  private final CollegeClient clientB;
  private final CollegeClient clientC;

  public FetchSharedCoursesHandler(CollegeClient clientB, CollegeClient clientC) {
    this.clientB = clientB;
    this.clientC = clientC;
  }

  @Override
  public Message handle(Message req) {
    try {
      String fromCollege = parseFromCollege(req.payload());
      Document merged = DocumentHelper.createDocument();
      Element root = merged.addElement("合并课程");

      // Fetch from B (if not the requesting college)
      if (!"B".equals(fromCollege)) {
        collectFromCollege(root, clientB, "B",
            XsltTransformer.fromClasspath("/xsl/formatB.xsl"),
            XsdValidator.fromClasspath("/schema/formatClass.xsd"));
      }
      // Fetch from C
      if (!"C".equals(fromCollege)) {
        collectFromCollege(root, clientC, "C",
            XsltTransformer.fromClasspath("/xsl/formatC.xsl"),
            XsdValidator.fromClasspath("/schema/formatClass.xsd"));
      }

      return Message.ok(req.requestId(), XmlIO.toPrettyString(merged));
    } catch (Exception e) {
      return Message.err(req.requestId(), "FETCH_FAILED", e.getMessage());
    }
  }

  private void collectFromCollege(Element root, CollegeClient client, String college,
      XsltTransformer toUnified, XsdValidator unifiedXsd) throws IOException {
    Message ask = new Message(Command.ASK_COURSE_INFO, UUID.randomUUID().toString(), "");
    Message resp = client.send(ask);
    if (resp.command() != Command.OK) return;

    String unifiedXml = toUnified.transform(resp.payload());
    var result = unifiedXsd.validate(unifiedXml);
    if (!result.valid()) {
      System.err.println("XSD validation failed for college " + college + ": " + result.errors());
    }
    // Append unified courses to merged doc
    try {
      Document unifiedDoc = XmlIO.parse(unifiedXml);
      for (Object obj : unifiedDoc.getRootElement().elements("class")) {
        Element cls = (Element) obj;
        root.add(cls.createCopy());
      }
    } catch (Exception e) {
      System.err.println("Failed to parse unified XML from " + college + ": " + e.getMessage());
    }
  }

  private static String parseFromCollege(String xml) {
    try { return XmlIO.parse(xml).getRootElement().elementText("from"); }
    catch (Exception e) { return ""; }
  }
}
```

- [ ] **Step 3: 在 IntegrationServer.main 注册 handler**

```java
// IntegrationServer.main() 中
var clientB = new CollegeClient("127.0.0.1", 9002);
var clientC = new CollegeClient("127.0.0.1", 9003);
var router = new IntegrationRouter()
    .register(Command.PING, new PingHandler())
    .register(Command.FETCH_SHARED_COURSES, new FetchSharedCoursesHandler(clientB, clientC));
```

- [ ] **Step 4: 运行 + commit**

```bash
mvn -pl integration -am test -Dtest=FetchSharedCoursesHandlerTest -Dsurefire.failIfNoSpecifiedTests=false
git add integration/src/
git commit -m "feat(integration): fetch shared courses handler with xsl transformation"
```

---

## Task 4: College Server 的 ASK_COURSE_INFO handler

**Files:**
- Create: `college-a/src/main/java/college/a/server/handler/AskCourseInfoHandler.java`
- B/C 同步创建

College Server 收到 `ASK_COURSE_INFO` 后，查询本院全部课程（含 share='Y' 和 share='N'），以本院格式 XML 返回。

- [ ] **Step 1: 实现 + 注册**

```java
// college-a/src/main/java/college/a/server/handler/AskCourseInfoHandler.java
package college.a.server.handler;

import college.a.dao.CourseDao;
import college.a.xml.CourseAAdapter;
import cn.edu.di.protocol.Message;

public class AskCourseInfoHandler implements Handler {
  private final CourseDao courseDao;
  public AskCourseInfoHandler(CourseDao courseDao) { this.courseDao = courseDao; }

  @Override
  public Message handle(Message request) {
    return Message.ok(request.requestId(), CourseAAdapter.marshal(courseDao.findAll()));
  }
}
```

在各自 Server 的 main() 中注册：
```java
.register(Command.ASK_COURSE_INFO, new AskCourseInfoHandler(courseDao))
```

B/C 同理，使用各自的 CourseDao 和 Adapter。

- [ ] **Step 2: commit**

```bash
git add college-a/src/ college-b/src/ college-c/src/
git commit -m "feat(college): ask course info handler for cross-college sharing"
```

---

## Task 5: College Server 的 LIST_SHARED_COURSES — 调用 Integration

**Files:**
- Create: `college-a/src/main/java/college/a/server/handler/ListSharedCoursesHandler.java`
- Create: `college-b/src/main/java/college/b/server/handler/ListSharedCoursesHandler.java`
- Create: `college-c/src/main/java/college/c/server/handler/ListSharedCoursesHandler.java`
- Modify: 三院 `College{A,B,C}Server.java`(注册 handler)

客户端请求 `LIST_SHARED_COURSES` → College Server 调用 Integration Server (`FETCH_SHARED_COURSES`) → 获取合并后的统一格式课程 XML(根 `<classes>`)→ 用对应 XSL 转回本院格式返回。

注：plan 1/2 已就位的 ?toX.xsl(实际语义是 unified→X)如下，按本院代码选择即可：
- A 院:`/xsl/BtoA.xsl`
- B 院:`/xsl/AtoB.xsl`
- C 院:`/xsl/AtoC.xsl`

- [ ] **Step 1: 实现 ListSharedCoursesHandler（A 院）**

```java
// college-a/src/main/java/college/a/server/handler/ListSharedCoursesHandler.java
package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XsdValidator;
import cn.edu.di.xml.XsltTransformer;

import java.net.Socket;
import java.util.UUID;

public class ListSharedCoursesHandler implements Handler {

  private final String integrationHost;
  private final int integrationPort;
  private final String fromCollege;
  private final String toLocalXsl;

  public ListSharedCoursesHandler(String integrationHost, int integrationPort,
                                  String fromCollege, String toLocalXsl) {
    this.integrationHost = integrationHost;
    this.integrationPort = integrationPort;
    this.fromCollege = fromCollege;
    this.toLocalXsl = toLocalXsl;
  }

  @Override
  public Message handle(Message req) {
    try (var sock = new Socket(integrationHost, integrationPort)) {
      Message fetchReq = new Message(Command.FETCH_SHARED_COURSES,
          UUID.randomUUID().toString(), "<from>" + fromCollege + "</from>");
      Message.write(sock.getOutputStream(), fetchReq);
      Message fetchResp = Message.read(sock.getInputStream());

      if (fetchResp.command() != Command.OK) return fetchResp;

      String unifiedXml = fetchResp.payload();
      var result = XsdValidator.fromClasspath("/schema/formatClass.xsd").validate(unifiedXml);
      if (!result.valid()) {
        return Message.err(req.requestId(), "XML_SCHEMA", result.errors().toString());
      }
      String localXml = XsltTransformer.fromClasspath(toLocalXsl).transform(unifiedXml);
      return Message.ok(req.requestId(), localXml);
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
```

- [ ] **Step 2: 在 College{A,B,C}Server.main() 注册**

A 院:
```java
.register(Command.LIST_SHARED_COURSES,
    new ListSharedCoursesHandler(config.integrationHost, config.integrationPort, "A", "/xsl/BtoA.xsl"))
```

B 院:
```java
.register(Command.LIST_SHARED_COURSES,
    new ListSharedCoursesHandler(config.integrationHost, config.integrationPort, "B", "/xsl/AtoB.xsl"))
```

C 院:
```java
.register(Command.LIST_SHARED_COURSES,
    new ListSharedCoursesHandler(config.integrationHost, config.integrationPort, "C", "/xsl/AtoC.xsl"))
```

注意：`config.integrationHost` 和 `config.integrationPort` 是 `public final` 字段（见 Task 2），直接字段访问，不要写括号。

- [ ] **Step 3: 编译验证 + commit**

```bash
mvn -q -DskipTests compile
git add college-a/src/ college-b/src/ college-c/src/
git commit -m "feat(college): list shared courses via integration server with xsl transform"
```

---

## Task 6: 跨院选课 — CROSS_ENROLL + APPLY_CHOICE 流程

**Files:**
- Modify: 三院 `College{A,B,C}/.../handler/EnrollLocalHandler.java`(加 config + 跨院转发)
- Create: 三院 `College{A,B,C}/.../handler/ApplyChoiceHandler.java`
- Create: `integration/src/main/java/integration/server/handler/CrossEnrollHandler.java`
- Modify: 三院 `College{A,B,C}Server.java`(传 config 给 EnrollLocalHandler;注册 APPLY_CHOICE)
- Modify: `integration/src/main/java/integration/server/IntegrationServer.java`(加 clientA;注册 CROSS_ENROLL)

流程：源院收到 `ENROLL` → 看课程编号前缀 → 本院则原地处理；跨院则**改写** payload 为统一 `<crossEnroll>` 格式 → 发 `CROSS_ENROLL` 给 Integration → Integration 看 courseId 前缀 → 发 `APPLY_CHOICE` 给目标院 → 目标院落库。

统一 payload 格式（仅用于跨院流程，本院 ENROLL 仍用各院原格式）：

```xml
<crossEnroll>
  <courseId>BC001</courseId>
  <studentId>AS001</studentId>
  <fromCollege>A</fromCollege>
</crossEnroll>
```

注意学生表 FK：演示作业范围内，假设各院 `选课` 表对学生编号没有外键约束，跨院学号写入只在 `选课` 行存在；学生信息不复制到目标院。

- [ ] **Step 1: 修改三院 EnrollLocalHandler — 加 config + 跨院转发**

A 院 (`college-a/src/main/java/college/a/server/handler/EnrollLocalHandler.java`,完整覆盖现有文件)：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlException;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.server.CollegeServerConfig;
import org.dom4j.Document;
import org.dom4j.Element;

import java.net.Socket;
import java.util.UUID;

public class EnrollLocalHandler implements Handler {

  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public EnrollLocalHandler(CourseDao courseDao, ChoiceDao choiceDao, CollegeServerConfig config) {
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message request) {
    try {
      Document doc = XmlIO.parse(request.payload());
      Element root = doc.getRootElement();
      String courseId = root.elementText("课程编号");
      String studentId = root.elementText("学生编号");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossEnroll(request, courseId, studentId);
      }

      if (courseDao.findById(courseId).isEmpty()) {
        return Message.err(request.requestId(), "NO_SUCH_COURSE",
            "course not found: " + courseId);
      }
      if (choiceDao.exists(studentId, courseId)) {
        return Message.err(request.requestId(), "ALREADY_ENROLLED",
            "student already enrolled in this course");
      }
      choiceDao.enrollLocal(studentId, courseId);
      return Message.ok(request.requestId(), "");
    } catch (XmlException e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD",
          "invalid XML: " + e.getMessage());
    } catch (Exception e) {
      return Message.err(request.requestId(), "INTERNAL_ERROR",
          "enroll failed: " + e.getMessage());
    }
  }

  private Message forwardCrossEnroll(Message req, String courseId, String studentId) {
    String payload = "<crossEnroll>"
        + "<courseId>" + courseId + "</courseId>"
        + "<studentId>" + studentId + "</studentId>"
        + "<fromCollege>" + config.collegeCode + "</fromCollege>"
        + "</crossEnroll>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.CROSS_ENROLL, UUID.randomUUID().toString(), payload));
      return Message.read(sock.getInputStream());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
```

B 院 (`college-b/.../EnrollLocalHandler.java`,完整覆盖,与 A 同构,只是字段名和 DAO 方法不同)：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import college.b.server.CollegeServerConfig;

import java.net.Socket;
import java.util.UUID;

public class EnrollLocalHandler implements Handler {
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public EnrollLocalHandler(CourseDao courseDao, ChoiceDao choiceDao, CollegeServerConfig config) {
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message request) {
    try {
      var doc = XmlIO.parse(request.payload());
      String courseId = doc.getRootElement().elementText("课程编号");
      String studentId = doc.getRootElement().elementText("学号");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossEnroll(request, courseId, studentId);
      }

      var course = courseDao.findById(courseId);
      if (course.isEmpty()) return Message.err(request.requestId(), "NO_SUCH_COURSE", courseId);
      if (choiceDao.exists(studentId, courseId))
        return Message.err(request.requestId(), "ALREADY_ENROLLED", studentId + "/" + courseId);

      choiceDao.enroll(studentId, courseId);
      return Message.ok(request.requestId(), "");
    } catch (Exception e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }

  private Message forwardCrossEnroll(Message req, String courseId, String studentId) {
    String payload = "<crossEnroll>"
        + "<courseId>" + courseId + "</courseId>"
        + "<studentId>" + studentId + "</studentId>"
        + "<fromCollege>" + config.collegeCode + "</fromCollege>"
        + "</crossEnroll>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.CROSS_ENROLL, UUID.randomUUID().toString(), payload));
      return Message.read(sock.getInputStream());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
```

C 院 (`college-c/.../EnrollLocalHandler.java`,完整覆盖)：

```java
package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import college.c.server.CollegeServerConfig;

import java.net.Socket;
import java.util.UUID;

public class EnrollLocalHandler implements Handler {
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public EnrollLocalHandler(CourseDao courseDao, ChoiceDao choiceDao, CollegeServerConfig config) {
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message request) {
    try {
      var doc = XmlIO.parse(request.payload());
      String courseId = doc.getRootElement().elementText("Cno");
      String studentId = doc.getRootElement().elementText("Sno");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossEnroll(request, courseId, studentId);
      }

      if (courseDao.findById(courseId).isEmpty())
        return Message.err(request.requestId(), "NO_SUCH_COURSE", courseId);
      if (choiceDao.exists(studentId, courseId))
        return Message.err(request.requestId(), "ALREADY_ENROLLED", studentId + "/" + courseId);

      choiceDao.enroll(studentId, courseId);
      return Message.ok(request.requestId(), "");
    } catch (Exception e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }

  private Message forwardCrossEnroll(Message req, String courseId, String studentId) {
    String payload = "<crossEnroll>"
        + "<courseId>" + courseId + "</courseId>"
        + "<studentId>" + studentId + "</studentId>"
        + "<fromCollege>" + config.collegeCode + "</fromCollege>"
        + "</crossEnroll>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.CROSS_ENROLL, UUID.randomUUID().toString(), payload));
      return Message.read(sock.getInputStream());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
```

- [ ] **Step 2: 实现 CrossEnrollHandler(Integration Server)**

`integration/src/main/java/integration/server/handler/CrossEnrollHandler.java`：

```java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import integration.net.CollegeClient;

import java.util.UUID;

public class CrossEnrollHandler implements Handler {

  private final CollegeClient clientA;
  private final CollegeClient clientB;
  private final CollegeClient clientC;

  public CrossEnrollHandler(CollegeClient clientA, CollegeClient clientB, CollegeClient clientC) {
    this.clientA = clientA;
    this.clientB = clientB;
    this.clientC = clientC;
  }

  @Override
  public Message handle(Message req) {
    try {
      var doc = XmlIO.parse(req.payload());
      String courseId = doc.getRootElement().elementText("courseId");

      CollegeClient target = targetFor(courseId);
      if (target == null) return Message.err(req.requestId(), "UNKNOWN_COURSE", courseId);

      Message apply = target.send(new Message(Command.APPLY_CHOICE,
          UUID.randomUUID().toString(), req.payload()));
      return apply.command() == Command.OK
          ? Message.ok(req.requestId(), apply.payload())
          : Message.err(req.requestId(), "TARGET_REJECTED", apply.payload());
    } catch (Exception e) {
      return Message.err(req.requestId(), "CROSS_FAILED", e.getMessage());
    }
  }

  private CollegeClient targetFor(String courseId) {
    if (courseId == null) return null;
    if (courseId.startsWith("AC")) return clientA;
    if (courseId.startsWith("BC")) return clientB;
    if (courseId.startsWith("CC")) return clientC;
    return null;
  }
}
```

- [ ] **Step 3: 实现三院 ApplyChoiceHandler**

A 院 (`college-a/src/main/java/college/a/server/handler/ApplyChoiceHandler.java`)：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import org.dom4j.Element;

public class ApplyChoiceHandler implements Handler {
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;

  public ApplyChoiceHandler(CourseDao courseDao, ChoiceDao choiceDao) {
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      Element root = XmlIO.parse(req.payload()).getRootElement();
      String courseId = root.elementText("courseId");
      String studentId = root.elementText("studentId");
      String fromCollege = root.elementText("fromCollege");

      if (courseDao.findById(courseId).isEmpty()) {
        return Message.err(req.requestId(), "NO_SUCH_COURSE", courseId);
      }
      if (choiceDao.exists(studentId, courseId)) {
        return Message.err(req.requestId(), "ALREADY_ENROLLED", studentId + "/" + courseId);
      }
      choiceDao.enrollFromOther(studentId, courseId, fromCollege);
      return Message.ok(req.requestId(), "");
    } catch (Exception e) {
      return Message.err(req.requestId(), "APPLY_FAILED", e.getMessage());
    }
  }
}
```

B 院 (`college-b/.../ApplyChoiceHandler.java`,只调用 enroll)：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import org.dom4j.Element;

public class ApplyChoiceHandler implements Handler {
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;

  public ApplyChoiceHandler(CourseDao courseDao, ChoiceDao choiceDao) {
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      Element root = XmlIO.parse(req.payload()).getRootElement();
      String courseId = root.elementText("courseId");
      String studentId = root.elementText("studentId");

      if (courseDao.findById(courseId).isEmpty()) {
        return Message.err(req.requestId(), "NO_SUCH_COURSE", courseId);
      }
      if (choiceDao.exists(studentId, courseId)) {
        return Message.err(req.requestId(), "ALREADY_ENROLLED", studentId + "/" + courseId);
      }
      choiceDao.enroll(studentId, courseId);
      return Message.ok(req.requestId(), "");
    } catch (Exception e) {
      return Message.err(req.requestId(), "APPLY_FAILED", e.getMessage());
    }
  }
}
```

C 院 (`college-c/.../ApplyChoiceHandler.java`,与 B 同构,仅 package 不同)：

```java
package college.c.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import org.dom4j.Element;

public class ApplyChoiceHandler implements Handler {
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;

  public ApplyChoiceHandler(CourseDao courseDao, ChoiceDao choiceDao) {
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      Element root = XmlIO.parse(req.payload()).getRootElement();
      String courseId = root.elementText("courseId");
      String studentId = root.elementText("studentId");

      if (courseDao.findById(courseId).isEmpty()) {
        return Message.err(req.requestId(), "NO_SUCH_COURSE", courseId);
      }
      if (choiceDao.exists(studentId, courseId)) {
        return Message.err(req.requestId(), "ALREADY_ENROLLED", studentId + "/" + courseId);
      }
      choiceDao.enroll(studentId, courseId);
      return Message.ok(req.requestId(), "");
    } catch (Exception e) {
      return Message.err(req.requestId(), "APPLY_FAILED", e.getMessage());
    }
  }
}
```

- [ ] **Step 4: 三院 main 调整 EnrollLocalHandler 构造参数 + 注册 APPLY_CHOICE**

三院 `College{X}Server.main()` 中：
1. `new EnrollLocalHandler(courseDao, choiceDao)` → `new EnrollLocalHandler(courseDao, choiceDao, config)`
2. 链尾追加 `.register(Command.APPLY_CHOICE, new ApplyChoiceHandler(courseDao, choiceDao))`
3. main 顶部加 `import college.{a,b,c}.server.handler.ApplyChoiceHandler;`

- [ ] **Step 5: IntegrationServer.main 加 clientA + 注册 CROSS_ENROLL**

`integration/src/main/java/integration/server/IntegrationServer.java` main：
```java
var clientA = new CollegeClient("127.0.0.1", 9001);
var clientB = new CollegeClient("127.0.0.1", 9002);
var clientC = new CollegeClient("127.0.0.1", 9003);
IntegrationRouter router = new IntegrationRouter()
    .register(Command.PING, new PingHandler())
    .register(Command.FETCH_SHARED_COURSES, new FetchSharedCoursesHandler(clientB, clientC))
    .register(Command.CROSS_ENROLL, new CrossEnrollHandler(clientA, clientB, clientC));
```
顶部加 `import integration.server.handler.CrossEnrollHandler;`

- [ ] **Step 6: 编译 + commit**

```bash
mvn -q -DskipTests compile
git add college-a/src/ college-b/src/ college-c/src/ integration/src/
git commit -m "feat(cross): cross-college enroll via integration server"
```

---

## Task 7: 跨院退课 — CROSS_WITHDRAW + REVOKE_CHOICE 流程

**Files:**
- Modify: 三院 `College{A,B,C}/.../handler/WithdrawLocalHandler.java`(加 config + 跨院转发)
- Create: 三院 `College{A,B,C}/.../handler/RevokeChoiceHandler.java`
- Create: `integration/src/main/java/integration/server/handler/CrossWithdrawHandler.java`
- Modify: 三院 `College{A,B,C}Server.java`(传 config 给 WithdrawLocalHandler;注册 REVOKE_CHOICE)
- Modify: `integration/src/main/java/integration/server/IntegrationServer.java`(注册 CROSS_WITHDRAW)

与 Task 6 对称：源院收到 `WITHDRAW` → 看课程编号前缀 → 本院则原地处理；跨院则改写 payload 为统一 `<crossWithdraw>` 格式 → 发 `CROSS_WITHDRAW` 给 Integration → Integration 看 courseId 前缀 → 发 `REVOKE_CHOICE` 给目标院 → 目标院删除选课记录。

统一 payload 格式（与 Task 6 同构，仅根标签和命令不同）：

```xml
<crossWithdraw>
  <courseId>BC001</courseId>
  <studentId>AS001</studentId>
  <fromCollege>A</fromCollege>
</crossWithdraw>
```

- [ ] **Step 1: 覆盖三院 WithdrawLocalHandler — 加 config + 跨院转发**

A 院 (`college-a/src/main/java/college/a/server/handler/WithdrawLocalHandler.java`,完全覆盖)：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlException;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.server.CollegeServerConfig;
import org.dom4j.Document;
import org.dom4j.Element;

import java.net.Socket;
import java.util.UUID;

public class WithdrawLocalHandler implements Handler {

  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public WithdrawLocalHandler(ChoiceDao choiceDao, CollegeServerConfig config) {
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message request) {
    try {
      Document doc = XmlIO.parse(request.payload());
      Element root = doc.getRootElement();
      String courseId = root.elementText("课程编号");
      String studentId = root.elementText("学生编号");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossWithdraw(request, courseId, studentId);
      }

      int rows = choiceDao.withdraw(studentId, courseId);
      if (rows == 0) {
        return Message.err(request.requestId(), "NO_SUCH_CHOICE",
            "no enrollment record found");
      }
      return Message.ok(request.requestId(), "");
    } catch (XmlException e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD",
          "invalid XML: " + e.getMessage());
    } catch (Exception e) {
      return Message.err(request.requestId(), "INTERNAL_ERROR",
          "withdraw failed: " + e.getMessage());
    }
  }

  private Message forwardCrossWithdraw(Message req, String courseId, String studentId) {
    String payload = "<crossWithdraw>"
        + "<courseId>" + courseId + "</courseId>"
        + "<studentId>" + studentId + "</studentId>"
        + "<fromCollege>" + config.collegeCode + "</fromCollege>"
        + "</crossWithdraw>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.CROSS_WITHDRAW, UUID.randomUUID().toString(), payload));
      return Message.read(sock.getInputStream());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
```

B 院 (`college-b/.../WithdrawLocalHandler.java`,完全覆盖)：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.b.dao.ChoiceDao;
import college.b.server.CollegeServerConfig;

import java.net.Socket;
import java.util.UUID;

public class WithdrawLocalHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public WithdrawLocalHandler(ChoiceDao choiceDao, CollegeServerConfig config) {
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message request) {
    try {
      var doc = XmlIO.parse(request.payload());
      String courseId = doc.getRootElement().elementText("课程编号");
      String studentId = doc.getRootElement().elementText("学号");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossWithdraw(request, courseId, studentId);
      }

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(request.requestId(), "")
          : Message.err(request.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }

  private Message forwardCrossWithdraw(Message req, String courseId, String studentId) {
    String payload = "<crossWithdraw>"
        + "<courseId>" + courseId + "</courseId>"
        + "<studentId>" + studentId + "</studentId>"
        + "<fromCollege>" + config.collegeCode + "</fromCollege>"
        + "</crossWithdraw>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.CROSS_WITHDRAW, UUID.randomUUID().toString(), payload));
      return Message.read(sock.getInputStream());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
```

C 院 (`college-c/.../WithdrawLocalHandler.java`,完全覆盖)：

```java
package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.ChoiceDao;
import college.c.server.CollegeServerConfig;

import java.net.Socket;
import java.util.UUID;

public class WithdrawLocalHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public WithdrawLocalHandler(ChoiceDao choiceDao, CollegeServerConfig config) {
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message request) {
    try {
      var doc = XmlIO.parse(request.payload());
      String courseId = doc.getRootElement().elementText("Cno");
      String studentId = doc.getRootElement().elementText("Sno");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossWithdraw(request, courseId, studentId);
      }

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(request.requestId(), "")
          : Message.err(request.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }

  private Message forwardCrossWithdraw(Message req, String courseId, String studentId) {
    String payload = "<crossWithdraw>"
        + "<courseId>" + courseId + "</courseId>"
        + "<studentId>" + studentId + "</studentId>"
        + "<fromCollege>" + config.collegeCode + "</fromCollege>"
        + "</crossWithdraw>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.CROSS_WITHDRAW, UUID.randomUUID().toString(), payload));
      return Message.read(sock.getInputStream());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
```

- [ ] **Step 2: 创建 integration CrossWithdrawHandler.java**

```java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import integration.net.CollegeClient;

import java.util.UUID;

public class CrossWithdrawHandler implements Handler {

  private final CollegeClient clientA;
  private final CollegeClient clientB;
  private final CollegeClient clientC;

  public CrossWithdrawHandler(CollegeClient clientA, CollegeClient clientB, CollegeClient clientC) {
    this.clientA = clientA;
    this.clientB = clientB;
    this.clientC = clientC;
  }

  @Override
  public Message handle(Message req) {
    try {
      var doc = XmlIO.parse(req.payload());
      String courseId = doc.getRootElement().elementText("courseId");

      CollegeClient target = targetFor(courseId);
      if (target == null) return Message.err(req.requestId(), "UNKNOWN_COURSE", courseId);

      Message revoke = target.send(new Message(Command.REVOKE_CHOICE,
          UUID.randomUUID().toString(), req.payload()));
      return revoke.command() == Command.OK
          ? Message.ok(req.requestId(), revoke.payload())
          : Message.err(req.requestId(), "TARGET_REJECTED", revoke.payload());
    } catch (Exception e) {
      return Message.err(req.requestId(), "CROSS_FAILED", e.getMessage());
    }
  }

  private CollegeClient targetFor(String courseId) {
    if (courseId == null) return null;
    if (courseId.startsWith("AC")) return clientA;
    if (courseId.startsWith("BC")) return clientB;
    if (courseId.startsWith("CC")) return clientC;
    return null;
  }
}
```

- [ ] **Step 3: 创建三院 RevokeChoiceHandler.java**

A 院 (`college-a/.../handler/RevokeChoiceHandler.java`,新建)：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import org.dom4j.Element;

public class RevokeChoiceHandler implements Handler {
  private final ChoiceDao choiceDao;

  public RevokeChoiceHandler(ChoiceDao choiceDao) {
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      Element root = XmlIO.parse(req.payload()).getRootElement();
      String courseId = root.elementText("courseId");
      String studentId = root.elementText("studentId");

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(req.requestId(), "")
          : Message.err(req.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(req.requestId(), "REVOKE_FAILED", e.getMessage());
    }
  }
}
```

B 院 (`college-b/.../RevokeChoiceHandler.java`,与 A 同构,仅 package 与 import 不同)：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.b.dao.ChoiceDao;
import org.dom4j.Element;

public class RevokeChoiceHandler implements Handler {
  private final ChoiceDao choiceDao;

  public RevokeChoiceHandler(ChoiceDao choiceDao) {
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      Element root = XmlIO.parse(req.payload()).getRootElement();
      String courseId = root.elementText("courseId");
      String studentId = root.elementText("studentId");

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(req.requestId(), "")
          : Message.err(req.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(req.requestId(), "REVOKE_FAILED", e.getMessage());
    }
  }
}
```

C 院 (`college-c/.../RevokeChoiceHandler.java`,新建)：

```java
package college.c.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.ChoiceDao;
import org.dom4j.Element;

public class RevokeChoiceHandler implements Handler {
  private final ChoiceDao choiceDao;

  public RevokeChoiceHandler(ChoiceDao choiceDao) {
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      Element root = XmlIO.parse(req.payload()).getRootElement();
      String courseId = root.elementText("courseId");
      String studentId = root.elementText("studentId");

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(req.requestId(), "")
          : Message.err(req.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(req.requestId(), "REVOKE_FAILED", e.getMessage());
    }
  }
}
```

- [ ] **Step 4: 三院 main 调整 WithdrawLocalHandler 构造参数 + 注册 REVOKE_CHOICE**

每院：
1. `new WithdrawLocalHandler(choiceDao)` → `new WithdrawLocalHandler(choiceDao, config)`
2. 链尾追加 `.register(Command.REVOKE_CHOICE, new RevokeChoiceHandler(choiceDao))`
3. main 顶部加 `import college.{a,b,c}.server.handler.RevokeChoiceHandler;`

- [ ] **Step 5: IntegrationServer.main 注册 CROSS_WITHDRAW**

链尾追加：
```java
.register(Command.CROSS_WITHDRAW, new CrossWithdrawHandler(clientA, clientB, clientC));
```
顶部加 `import integration.server.handler.CrossWithdrawHandler;`

- [ ] **Step 6: 编译 + commit**

```bash
mvn -q -DskipTests compile
git add college-a/src/ college-b/src/ college-c/src/ integration/src/
git commit -m "feat(cross): cross-college withdraw via integration server"
```

---

## Task 8: 全局统计 — STATS_GLOBAL 流程

**Files:**
- Modify: 三院 `ChoiceDao.java`(加 `findAll()`)
- Create: 三院 `College{A,B,C}/.../handler/StatsForwardHandler.java`(处理 STATS_GLOBAL → 转发 integration)
- Create: 三院 `College{A,B,C}/.../handler/StatsPullHandler.java`(处理 STATS_PULL → 返回本院统计)
- Create: `integration/src/main/java/integration/server/handler/StatsGlobalHandler.java`
- Modify: 三院 `College{A,B,C}Server.java`(注册 STATS_GLOBAL + STATS_PULL)
- Modify: `integration/src/main/java/integration/server/IntegrationServer.java`(注册 STATS_GLOBAL)

流程：Client → 院.STATS_GLOBAL → Integration.STATS_GLOBAL → 三院.STATS_PULL(并发) → 各院返回 OK 内嵌 unified XML → Integration 聚合 → 返回 Client。

各院 STATS_PULL 返回的 unified 格式：

```xml
<pullData college="A">
  <studentCount>50</studentCount>
  <courseCount>10</courseCount>
  <sharedCount>4</sharedCount>
  <crossEnrollmentCount>5</crossEnrollmentCount>
  <courses>
    <course id="AC001" name="数据库原理" enrollments="28"/>
    ...
  </courses>
</pullData>
```

跨院选课判定：本院 `选课` 表中学生编号**不以本院 studentIdPrefix 开头**即为外院学生跨选本院课。

Integration 返回的 unified 报表：

```xml
<stats>
  <summary>
    <totalStudents>150</totalStudents>
    <totalCourses>30</totalCourses>
    <totalSharedCourses>12</totalSharedCourses>
    <crossEnrollments>15</crossEnrollments>
  </summary>
  <byCollege>
    <college code="A" students="50" courses="10" shared="4" crossEnrollments="5"/>
    <college code="B" students="50" courses="10" shared="4" crossEnrollments="6"/>
    <college code="C" students="50" courses="10" shared="4" crossEnrollments="4"/>
  </byCollege>
  <topCourses>
    <course id="BC001" name="..." enrollments="28"/>
    ...(top 5)
  </topCourses>
</stats>
```

- [ ] **Step 1: 三院 ChoiceDao 加 `findAll()`**

A 院 `college-a/src/main/java/college/a/dao/ChoiceDao.java`,在 `findByStudent` 之前**追加方法**(不改其他方法)：

```java
  public List<Row> findAll() {
    String sql = "SELECT 课程编号,学生编号,成绩,来源 FROM 选课";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }
```

B 院 `college-b/src/main/java/college/b/dao/ChoiceDao.java`,追加：

```java
  public List<Row> findAll() {
    String sql = "SELECT 课程编号,学号,得分 FROM 选课";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3)));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }
```

C 院 `college-c/src/main/java/college/c/dao/ChoiceDao.java`,追加：

```java
  public List<Row> findAll() {
    String sql = "SELECT Cno, Sno, Grd FROM 选课";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3)));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }
```

- [ ] **Step 2: 三院 StatsForwardHandler.java**

A 院 (`college-a/.../handler/StatsForwardHandler.java`,新建)：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.server.CollegeServerConfig;

import java.net.Socket;
import java.util.UUID;

public class StatsForwardHandler implements Handler {
  private final CollegeServerConfig config;

  public StatsForwardHandler(CollegeServerConfig config) {
    this.config = config;
  }

  @Override
  public Message handle(Message req) {
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.STATS_GLOBAL, UUID.randomUUID().toString(), ""));
      Message resp = Message.read(sock.getInputStream());
      return resp.command() == Command.OK
          ? Message.ok(req.requestId(), resp.payload())
          : Message.err(req.requestId(), "STATS_FAILED", resp.payload());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
```

B 院 (`college-b/.../StatsForwardHandler.java`)：与 A 同构,仅 `package college.b.server.handler;` + `import college.b.server.CollegeServerConfig;`。

C 院 (`college-c/.../StatsForwardHandler.java`)：与 A 同构,仅 package + import 改为 c。

- [ ] **Step 3: 三院 StatsPullHandler.java**

A 院 (`college-a/.../handler/StatsPullHandler.java`,新建)：

```java
package college.a.server.handler;

import cn.edu.di.protocol.Message;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.dao.StudentDao;
import college.a.server.CollegeServerConfig;

import java.util.HashMap;
import java.util.Map;

public class StatsPullHandler implements Handler {
  private final StudentDao studentDao;
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public StatsPullHandler(StudentDao studentDao, CourseDao courseDao,
                          ChoiceDao choiceDao, CollegeServerConfig config) {
    this.studentDao = studentDao;
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message req) {
    try {
      var students = studentDao.findAll();
      var courses = courseDao.findAll();
      var shared = courseDao.findShared();
      var choices = choiceDao.findAll();

      Map<String, Integer> enrollByCourse = new HashMap<>();
      int crossCount = 0;
      for (var ch : choices) {
        enrollByCourse.merge(ch.courseId(), 1, Integer::sum);
        if (!ch.studentId().startsWith(config.studentIdPrefix)) crossCount++;
      }

      StringBuilder sb = new StringBuilder();
      sb.append("<pullData college=\"").append(config.collegeCode).append("\">");
      sb.append("<studentCount>").append(students.size()).append("</studentCount>");
      sb.append("<courseCount>").append(courses.size()).append("</courseCount>");
      sb.append("<sharedCount>").append(shared.size()).append("</sharedCount>");
      sb.append("<crossEnrollmentCount>").append(crossCount).append("</crossEnrollmentCount>");
      sb.append("<courses>");
      for (var c : courses) {
        sb.append("<course id=\"").append(c.id())
          .append("\" name=\"").append(c.name())
          .append("\" enrollments=\"").append(enrollByCourse.getOrDefault(c.id(), 0))
          .append("\"/>");
      }
      sb.append("</courses>");
      sb.append("</pullData>");

      return Message.ok(req.requestId(), sb.toString());
    } catch (Exception e) {
      return Message.err(req.requestId(), "PULL_FAILED", e.getMessage());
    }
  }
}
```

B 院 (`college-b/.../StatsPullHandler.java`,与 A 同构,仅 package + DAO import 不同)：

```java
package college.b.server.handler;

import cn.edu.di.protocol.Message;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import college.b.dao.StudentDao;
import college.b.server.CollegeServerConfig;

import java.util.HashMap;
import java.util.Map;

public class StatsPullHandler implements Handler {
  private final StudentDao studentDao;
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public StatsPullHandler(StudentDao studentDao, CourseDao courseDao,
                          ChoiceDao choiceDao, CollegeServerConfig config) {
    this.studentDao = studentDao;
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message req) {
    try {
      var students = studentDao.findAll();
      var courses = courseDao.findAll();
      var shared = courseDao.findShared();
      var choices = choiceDao.findAll();

      Map<String, Integer> enrollByCourse = new HashMap<>();
      int crossCount = 0;
      for (var ch : choices) {
        enrollByCourse.merge(ch.courseId(), 1, Integer::sum);
        if (!ch.studentId().startsWith(config.studentIdPrefix)) crossCount++;
      }

      StringBuilder sb = new StringBuilder();
      sb.append("<pullData college=\"").append(config.collegeCode).append("\">");
      sb.append("<studentCount>").append(students.size()).append("</studentCount>");
      sb.append("<courseCount>").append(courses.size()).append("</courseCount>");
      sb.append("<sharedCount>").append(shared.size()).append("</sharedCount>");
      sb.append("<crossEnrollmentCount>").append(crossCount).append("</crossEnrollmentCount>");
      sb.append("<courses>");
      for (var c : courses) {
        sb.append("<course id=\"").append(c.id())
          .append("\" name=\"").append(c.name())
          .append("\" enrollments=\"").append(enrollByCourse.getOrDefault(c.id(), 0))
          .append("\"/>");
      }
      sb.append("</courses>");
      sb.append("</pullData>");

      return Message.ok(req.requestId(), sb.toString());
    } catch (Exception e) {
      return Message.err(req.requestId(), "PULL_FAILED", e.getMessage());
    }
  }
}
```

C 院 (`college-c/.../StatsPullHandler.java`,与 B 同构,仅 package + DAO import 改为 c)。

- [ ] **Step 4: integration StatsGlobalHandler.java**

`integration/src/main/java/integration/server/handler/StatsGlobalHandler.java`：

```java
package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import integration.net.CollegeClient;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class StatsGlobalHandler implements Handler {

  private final CollegeClient clientA;
  private final CollegeClient clientB;
  private final CollegeClient clientC;

  public StatsGlobalHandler(CollegeClient clientA, CollegeClient clientB, CollegeClient clientC) {
    this.clientA = clientA;
    this.clientB = clientB;
    this.clientC = clientC;
  }

  private record CollegeStat(String code, int students, int courses, int shared, int cross) {}
  private record CourseEntry(String id, String name, int enrollments) {}

  @Override
  public Message handle(Message req) {
    try {
      var stats = new ArrayList<CollegeStat>();
      var allCourses = new ArrayList<CourseEntry>();

      for (CollegeClient client : List.of(clientA, clientB, clientC)) {
        Message resp = client.send(new Message(Command.STATS_PULL, UUID.randomUUID().toString(), ""));
        if (resp.command() != Command.OK) {
          return Message.err(req.requestId(), "PULL_FAILED", resp.payload());
        }
        Element root = XmlIO.parse(resp.payload()).getRootElement();
        String code = root.attributeValue("college");
        int students = Integer.parseInt(root.elementText("studentCount"));
        int courses = Integer.parseInt(root.elementText("courseCount"));
        int shared = Integer.parseInt(root.elementText("sharedCount"));
        int cross = Integer.parseInt(root.elementText("crossEnrollmentCount"));
        stats.add(new CollegeStat(code, students, courses, shared, cross));

        Element coursesEl = root.element("courses");
        for (Object o : coursesEl.elements("course")) {
          Element ce = (Element) o;
          allCourses.add(new CourseEntry(
              ce.attributeValue("id"),
              ce.attributeValue("name"),
              Integer.parseInt(ce.attributeValue("enrollments"))));
        }
      }

      int totalStudents = stats.stream().mapToInt(CollegeStat::students).sum();
      int totalCourses = stats.stream().mapToInt(CollegeStat::courses).sum();
      int totalShared = stats.stream().mapToInt(CollegeStat::shared).sum();
      int totalCross = stats.stream().mapToInt(CollegeStat::cross).sum();

      allCourses.sort(Comparator.comparingInt(CourseEntry::enrollments).reversed());
      var top5 = allCourses.stream().limit(5).toList();

      StringBuilder sb = new StringBuilder();
      sb.append("<stats>");
      sb.append("<summary>");
      sb.append("<totalStudents>").append(totalStudents).append("</totalStudents>");
      sb.append("<totalCourses>").append(totalCourses).append("</totalCourses>");
      sb.append("<totalSharedCourses>").append(totalShared).append("</totalSharedCourses>");
      sb.append("<crossEnrollments>").append(totalCross).append("</crossEnrollments>");
      sb.append("</summary>");
      sb.append("<byCollege>");
      for (var s : stats) {
        sb.append("<college code=\"").append(s.code())
          .append("\" students=\"").append(s.students())
          .append("\" courses=\"").append(s.courses())
          .append("\" shared=\"").append(s.shared())
          .append("\" crossEnrollments=\"").append(s.cross())
          .append("\"/>");
      }
      sb.append("</byCollege>");
      sb.append("<topCourses>");
      for (var c : top5) {
        sb.append("<course id=\"").append(c.id())
          .append("\" name=\"").append(c.name())
          .append("\" enrollments=\"").append(c.enrollments())
          .append("\"/>");
      }
      sb.append("</topCourses>");
      sb.append("</stats>");

      return Message.ok(req.requestId(), sb.toString());
    } catch (Exception e) {
      return Message.err(req.requestId(), "STATS_FAILED", e.getMessage());
    }
  }
}
```

- [ ] **Step 5: 三院 main 注册 STATS_GLOBAL + STATS_PULL**

每院 `College{X}Server.main()`：

1. main 中创建 `studentDao`(若没有则补 `new StudentDao(ds)`)
2. 链尾追加：
   ```java
   .register(Command.STATS_GLOBAL, new StatsForwardHandler(config))
   .register(Command.STATS_PULL, new StatsPullHandler(studentDao, courseDao, choiceDao, config))
   ```
3. main 顶部加 `import college.{a,b,c}.dao.StudentDao;`(若没有)、`import college.{a,b,c}.server.handler.StatsForwardHandler;`、`import college.{a,b,c}.server.handler.StatsPullHandler;`

- [ ] **Step 6: IntegrationServer.main 注册 STATS_GLOBAL**

链尾追加：
```java
.register(Command.STATS_GLOBAL, new StatsGlobalHandler(clientA, clientB, clientC));
```
顶部加 `import integration.server.handler.StatsGlobalHandler;`

- [ ] **Step 7: 编译 + commit**

```bash
mvn -q -DskipTests compile
git add college-a/src/ college-b/src/ college-c/src/ integration/src/
git commit -m "feat(stats): global stats aggregation across colleges"
```

---

## Task 9: 客户端支持共享课程和跨院选课/退课

**Files:**
- Modify: `client/src/main/java/client/ui/CourseListFrame.java` — 增加共享课程视图、选课/退课按钮、全局统计窗口

**说明：**
- `Main.java` 的 `--college=B|C` 参数解析在 Plan 2 已完成，本任务不再修改。
- `populateTable` 已支持三院 XML（A/B 用 `<课程集>/<课程>`，C 用 `<courses>/<course>`），共享课程经 College Server 内置 XSL（A→BtoA / B→AtoB / C→AtoC）转回本院格式后复用同一解析逻辑。
- 选课/退课按钮发送本院字段名的 ENROLL/WITHDRAW（A：`课程编号`+`学生编号`，B：`课程编号`+`学号`，C：`Cno`+`Sno`），跨院路由由 College Server 内部完成（Task 6/7）。
- studentId 通过 JOptionPane 输入框获取，默认值为登录时的 username（约定教师/学生账号即学号）。
- 全局统计按钮发送 `STATS_GLOBAL`，弹出 JDialog 展示 summary、各院明细、Top5 课程。

- [ ] **Step 1: 重写 CourseListFrame.java — 全部代码**

将整个文件替换为下列内容（在原刷新按钮旁追加 4 个新按钮 + 新窗口）：

```java
package client.ui;

import client.net.CollegeClient;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

public class CourseListFrame extends JFrame {

  private final String college;
  private final String username;
  private final String role;
  private final CollegeClient client;
  private final DefaultTableModel tableModel;
  private final JTable table;
  private final JLabel statusLabel;
  private final JButton refreshLocalButton;
  private final JButton refreshSharedButton;
  private final JButton enrollButton;
  private final JButton withdrawButton;
  private final JButton statsButton;

  private static final String[] COLUMNS = {
      "课程编号", "课程名称", "学分", "授课老师", "授课地点", "共享"
  };

  public CourseListFrame(String college, String username, String role, CollegeClient client) {
    this.college = college;
    this.username = username;
    this.role = role;
    this.client = client;

    setTitle("学院 " + college + "  欢迎 " + username + "（" + role + "）");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(820, 460);
    setLocationRelativeTo(null);

    var mainPanel = new JPanel(new BorderLayout());

    tableModel = new DefaultTableModel(COLUMNS, 0) {
      @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    table = new JTable(tableModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);

    var buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
    refreshLocalButton = new JButton("刷新本院课程");
    refreshSharedButton = new JButton("刷新共享课程");
    enrollButton = new JButton("选课");
    withdrawButton = new JButton("退课");
    statsButton = new JButton("全局统计");
    refreshLocalButton.addActionListener(e -> loadLocalCourses());
    refreshSharedButton.addActionListener(e -> loadSharedCourses());
    enrollButton.addActionListener(e -> doEnroll());
    withdrawButton.addActionListener(e -> doWithdraw());
    statsButton.addActionListener(e -> doStats());
    buttonPanel.add(refreshLocalButton);
    buttonPanel.add(refreshSharedButton);
    buttonPanel.add(enrollButton);
    buttonPanel.add(withdrawButton);
    buttonPanel.add(statsButton);

    statusLabel = new JLabel(" ", SwingConstants.CENTER);

    var bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.add(buttonPanel, BorderLayout.CENTER);
    bottomPanel.add(statusLabel, BorderLayout.SOUTH);
    mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    add(mainPanel);

    loadLocalCourses();
  }

  private void loadLocalCourses() {
    sendAndPopulate(new Message(Command.LIST_LOCAL_COURSES, UUID.randomUUID().toString(), ""),
        "本院课程");
  }

  private void loadSharedCourses() {
    sendAndPopulate(new Message(Command.LIST_SHARED_COURSES, UUID.randomUUID().toString(), ""),
        "共享课程");
  }

  private void sendAndPopulate(Message req, String label) {
    statusLabel.setText("正在加载" + label + "...");
    setButtonsEnabled(false);
    new Thread(() -> {
      try {
        Message res = client.send(req);
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            populateTable(res.payload());
            statusLabel.setText(label + "加载完成");
          } else if (res.command() == Command.ERR) {
            String detail = parseErrorDetail(res.payload());
            statusLabel.setText(label + "加载失败: " + detail);
            JOptionPane.showMessageDialog(this,
                "加载" + label + "失败: " + detail, "错误", JOptionPane.ERROR_MESSAGE);
          } else {
            statusLabel.setText(label + "加载失败: 未知响应");
          }
          setButtonsEnabled(true);
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("网络错误: " + e.getMessage());
          setButtonsEnabled(true);
          JOptionPane.showMessageDialog(this,
              "网络错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        });
      }
    }).start();
  }

  private void doEnroll() { sendChoice(Command.ENROLL, "选课"); }
  private void doWithdraw() { sendChoice(Command.WITHDRAW, "退课"); }

  private void sendChoice(Command cmd, String label) {
    int row = table.getSelectedRow();
    if (row < 0) {
      JOptionPane.showMessageDialog(this, "请先选中课程行", "提示", JOptionPane.WARNING_MESSAGE);
      return;
    }
    String courseId = String.valueOf(tableModel.getValueAt(row, 0));
    String studentId = JOptionPane.showInputDialog(this,
        label + "学生编号:", username);
    if (studentId == null || studentId.isBlank()) return;

    String payload = buildChoicePayload(courseId, studentId.trim());
    statusLabel.setText("正在" + label + "...");
    setButtonsEnabled(false);
    new Thread(() -> {
      try {
        Message res = client.send(new Message(cmd, UUID.randomUUID().toString(), payload));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            statusLabel.setText(label + "成功: " + studentId.trim() + " / " + courseId);
            JOptionPane.showMessageDialog(this, label + "成功", "提示",
                JOptionPane.INFORMATION_MESSAGE);
          } else {
            String detail = parseErrorDetail(res.payload());
            statusLabel.setText(label + "失败: " + detail);
            JOptionPane.showMessageDialog(this, label + "失败: " + detail, "错误",
                JOptionPane.ERROR_MESSAGE);
          }
          setButtonsEnabled(true);
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("网络错误: " + e.getMessage());
          setButtonsEnabled(true);
        });
      }
    }).start();
  }

  private String buildChoicePayload(String courseId, String studentId) {
    String cid = esc(courseId);
    String sid = esc(studentId);
    if ("C".equals(college)) {
      return "<choice><Cno>" + cid + "</Cno><Sno>" + sid + "</Sno></choice>";
    } else if ("B".equals(college)) {
      return "<choice><课程编号>" + cid + "</课程编号><学号>" + sid + "</学号></choice>";
    } else {
      return "<choice><课程编号>" + cid + "</课程编号><学生编号>" + sid + "</学生编号></choice>";
    }
  }

  private void doStats() {
    statusLabel.setText("正在拉取全局统计...");
    setButtonsEnabled(false);
    new Thread(() -> {
      try {
        Message res = client.send(new Message(Command.STATS_GLOBAL,
            UUID.randomUUID().toString(), ""));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            statusLabel.setText("全局统计已加载");
            showStatsDialog(res.payload());
          } else {
            String detail = parseErrorDetail(res.payload());
            statusLabel.setText("统计失败: " + detail);
            JOptionPane.showMessageDialog(this, "统计失败: " + detail, "错误",
                JOptionPane.ERROR_MESSAGE);
          }
          setButtonsEnabled(true);
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("网络错误: " + e.getMessage());
          setButtonsEnabled(true);
        });
      }
    }).start();
  }

  private void showStatsDialog(String xml) {
    StringBuilder sb = new StringBuilder();
    try {
      var root = XmlIO.parse(xml).getRootElement();
      var summary = root.element("summary");
      sb.append("【全局汇总】\n");
      sb.append("  学生总数: ").append(summary.elementText("totalStudents")).append("\n");
      sb.append("  课程总数: ").append(summary.elementText("totalCourses")).append("\n");
      sb.append("  共享课程总数: ").append(summary.elementText("totalSharedCourses")).append("\n");
      sb.append("  跨院选课总数: ").append(summary.elementText("crossEnrollments")).append("\n\n");

      sb.append("【各院明细】\n");
      var byCollege = root.element("byCollege");
      for (Object o : byCollege.elements("college")) {
        var c = (org.dom4j.Element) o;
        sb.append("  学院 ").append(c.attributeValue("code"))
          .append(": 学生=").append(c.attributeValue("students"))
          .append(", 课程=").append(c.attributeValue("courses"))
          .append(", 共享=").append(c.attributeValue("shared"))
          .append(", 跨院选课=").append(c.attributeValue("crossEnrollments"))
          .append("\n");
      }
      sb.append("\n【Top 5 课程（按选课数）】\n");
      var top = root.element("topCourses");
      int rank = 1;
      for (Object o : top.elements("course")) {
        var c = (org.dom4j.Element) o;
        sb.append("  ").append(rank++).append(". ")
          .append(c.attributeValue("id")).append(" ")
          .append(c.attributeValue("name"))
          .append(" (").append(c.attributeValue("enrollments")).append(")\n");
      }
    } catch (Exception e) {
      sb.append("解析统计数据失败: ").append(e.getMessage()).append("\n\n").append(xml);
    }
    var area = new JTextArea(sb.toString(), 20, 50);
    area.setEditable(false);
    area.setFont(new Font("Monospaced", Font.PLAIN, 13));
    JOptionPane.showMessageDialog(this, new JScrollPane(area),
        "全局统计", JOptionPane.INFORMATION_MESSAGE);
  }

  private void setButtonsEnabled(boolean enabled) {
    refreshLocalButton.setEnabled(enabled);
    refreshSharedButton.setEnabled(enabled);
    enrollButton.setEnabled(enabled);
    withdrawButton.setEnabled(enabled);
    statsButton.setEnabled(enabled);
  }

  private void populateTable(String xml) {
    try {
      var doc = XmlIO.parse(xml);
      tableModel.setRowCount(0);
      String rootName = doc.getRootElement().getName();
      boolean isC = "courses".equals(rootName);

      for (var obj : doc.getRootElement().elements(isC ? "course" : "课程")) {
        var el = (org.dom4j.Element) obj;
        String id, name, score, teacher, location, shared;
        if (isC) {
          id = el.elementText("Cno"); name = el.elementText("Cnm");
          score = el.elementText("Cpt"); teacher = el.elementText("Tec");
          location = el.elementText("Pla"); shared = el.elementText("Share");
        } else if ("B".equals(college)) {
          id = el.elementText("编号"); name = el.elementText("名称");
          score = el.elementText("学分"); teacher = el.elementText("老师");
          location = el.elementText("地点"); shared = el.elementText("共享");
        } else {
          id = el.elementText("课程编号"); name = el.elementText("课程名称");
          score = el.elementText("学分"); teacher = el.elementText("授课老师");
          location = el.elementText("授课地点"); shared = el.elementText("共享");
        }
        tableModel.addRow(new Object[]{id, name, score, teacher, location, shared});
      }
    } catch (Exception e) {
      statusLabel.setText("解析数据失败: " + e.getMessage());
    }
  }

  private static String parseErrorDetail(String xml) {
    try {
      var doc = XmlIO.parse(xml);
      String detail = doc.getRootElement().elementText("detail");
      return (detail != null && !detail.isEmpty()) ? detail : "unknown error";
    } catch (Exception e) {
      return "unknown error";
    }
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn -pl client -am -DskipTests compile
```

预期：BUILD SUCCESS。

- [ ] **Step 3: commit**

```bash
git add client/src/main/java/client/ui/CourseListFrame.java
git commit -m "feat(client): shared courses + cross-college enroll/withdraw + global stats UI"
```

---

## Task 10: Integration Server 端到端集成测试

**Files:**
- Create: `integration/src/test/java/integration/server/CrossCollegeFlowsTest.java`

**说明：**
- 文件名用 `*Test.java`（不是 `*IT.java`），原因：surefire 默认仅识别 `*Test/*Tests/*TestCase`；用 `mvn ... test` 即可运行。
- 不依赖真实数据库或 socket：用 Mockito mock `integration.net.CollegeClient`，把三院的响应静态化；这样能在 `mvn test` 几秒内跑完。
- 覆盖 4 条用例：
  1. `CrossEnrollHandler` 按课程编号前缀路由到 clientB（`BC*` → clientB；clientA/clientC 不被调用）
  2. `CrossEnrollHandler` 未知前缀（`XX*`）返回 `UNKNOWN_COURSE` 错误
  3. `CrossWithdrawHandler` 按课程编号前缀路由到 clientC（`CC*` → clientC）
  4. `StatsGlobalHandler` 聚合三院 `<pullData>` 响应（汇总数 + Top5 排序）
- 不直接测 `FetchSharedCoursesHandler`：它依赖 classpath 上的 `formatB.xsl/formatC.xsl/formatClass.xsd`，且需要构造逼真的 B/C 原始 XML，复杂度高且 Plan 1/2 的单元测试已覆盖 XSL/XSD 工具类。

- [ ] **Step 1: 创建 CrossCollegeFlowsTest.java — 完整代码**

```java
package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import integration.server.handler.CrossEnrollHandler;
import integration.server.handler.CrossWithdrawHandler;
import integration.server.handler.StatsGlobalHandler;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossCollegeFlowsTest {

  @Test
  void crossEnroll_routes_by_course_prefix() throws Exception {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    when(clientB.send(any())).thenReturn(Message.ok("rid", ""));

    var handler = new CrossEnrollHandler(clientA, clientB, clientC);
    String payload = "<crossEnroll><courseId>BC001</courseId>"
        + "<studentId>AS001</studentId><fromCollege>A</fromCollege></crossEnroll>";
    Message res = handler.handle(new Message(Command.CROSS_ENROLL,
        UUID.randomUUID().toString(), payload));

    assertEquals(Command.OK, res.command());
    verify(clientB).send(any());
    verify(clientA, never()).send(any());
    verify(clientC, never()).send(any());
  }

  @Test
  void crossEnroll_unknown_prefix_returns_err() {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);

    var handler = new CrossEnrollHandler(clientA, clientB, clientC);
    String payload = "<crossEnroll><courseId>XX999</courseId>"
        + "<studentId>AS001</studentId><fromCollege>A</fromCollege></crossEnroll>";
    Message res = handler.handle(new Message(Command.CROSS_ENROLL,
        UUID.randomUUID().toString(), payload));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("UNKNOWN_COURSE"),
        "expected UNKNOWN_COURSE in payload, got: " + res.payload());
  }

  @Test
  void crossWithdraw_routes_by_course_prefix() throws Exception {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    when(clientC.send(any())).thenReturn(Message.ok("rid", ""));

    var handler = new CrossWithdrawHandler(clientA, clientB, clientC);
    String payload = "<crossWithdraw><courseId>CC005</courseId>"
        + "<studentId>BS010</studentId><fromCollege>B</fromCollege></crossWithdraw>";
    Message res = handler.handle(new Message(Command.CROSS_WITHDRAW,
        UUID.randomUUID().toString(), payload));

    assertEquals(Command.OK, res.command());
    verify(clientC).send(any());
    verify(clientA, never()).send(any());
    verify(clientB, never()).send(any());
  }

  @Test
  void statsGlobal_aggregates_three_colleges() throws Exception {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    when(clientA.send(any())).thenReturn(Message.ok("rid",
        pullReply("A", 50, 10, 3, 5,
            "AC001", "课程一", 30, "AC002", "课程二", 20)));
    when(clientB.send(any())).thenReturn(Message.ok("rid",
        pullReply("B", 50, 10, 4, 7,
            "BC001", "课程三", 28, "BC002", "课程四", 18)));
    when(clientC.send(any())).thenReturn(Message.ok("rid",
        pullReply("C", 50, 10, 2, 0,
            "CC001", "课程五", 25, "CC002", "课程六", 15)));

    var handler = new StatsGlobalHandler(clientA, clientB, clientC);
    Message res = handler.handle(new Message(Command.STATS_GLOBAL,
        UUID.randomUUID().toString(), ""));

    assertEquals(Command.OK, res.command());
    String xml = res.payload();
    assertTrue(xml.contains("<totalStudents>150</totalStudents>"), xml);
    assertTrue(xml.contains("<totalCourses>30</totalCourses>"), xml);
    assertTrue(xml.contains("<totalSharedCourses>9</totalSharedCourses>"), xml);
    assertTrue(xml.contains("<crossEnrollments>12</crossEnrollments>"), xml);

    int topIdx = xml.indexOf("<topCourses>");
    assertTrue(topIdx > 0, "topCourses section missing");
    String topSection = xml.substring(topIdx);
    int idxAC001 = topSection.indexOf("id=\"AC001\"");
    int idxBC001 = topSection.indexOf("id=\"BC001\"");
    assertTrue(idxAC001 > 0 && idxBC001 > 0, "Top entries missing: " + topSection);
    assertTrue(idxAC001 < idxBC001,
        "AC001(30) should rank before BC001(28) in: " + topSection);
  }

  private static String pullReply(String code, int students, int courses,
                                  int shared, int cross,
                                  String c1Id, String c1Name, int c1Enr,
                                  String c2Id, String c2Name, int c2Enr) {
    return "<pullData college=\"" + code + "\">"
        + "<studentCount>" + students + "</studentCount>"
        + "<courseCount>" + courses + "</courseCount>"
        + "<sharedCount>" + shared + "</sharedCount>"
        + "<crossEnrollmentCount>" + cross + "</crossEnrollmentCount>"
        + "<courses>"
        + "<course id=\"" + c1Id + "\" name=\"" + c1Name
        + "\" enrollments=\"" + c1Enr + "\"/>"
        + "<course id=\"" + c2Id + "\" name=\"" + c2Name
        + "\" enrollments=\"" + c2Enr + "\"/>"
        + "</courses>"
        + "</pullData>";
  }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn -pl integration -am -q test
```

预期：4/4 通过，BUILD SUCCESS。

- [ ] **Step 3: commit**

```bash
git add integration/src/test/java/integration/server/CrossCollegeFlowsTest.java
git commit -m "test(integration): cross-college routing + global stats aggregation"
```

---

## Task 11: Demo 脚本 + 冒烟清单

**Files:**
- Create: `docs/demo.md`
- Modify: `scripts/start-all.sh`（不需要改，验证即可）

- [ ] **Step 1: 写 demo 脚本**

```markdown
# Plan 3 Demo Script

## Setup
1. `./scripts/start-all.sh` — start all 5 processes
2. Launch 3 client windows (A, B, C)

## Demo Flow
1. **Login**: A client login as `as001`/`pwd001`
2. **Local courses**: Click "Refresh Local Courses" — see 10 A courses
3. **Shared courses**: Click "Refresh Shared Courses" — see B/C shared courses
4. **Cross enroll**: Select a B course (BC001), click "Enroll" — success
5. **Cross withdraw**: Click "Withdraw" on the same course — success
6. **Verify on B**: Login to B client as `bs001`, check that AS001 appears in student table
7. **Global stats**: Login as `admin` on A client, click "Global Stats"
8. **Error handling**: Send malformed XML to Integration Server
```

- [ ] **Step 2: 手工冒烟并记录**

- [ ] **Step 3: commit**

```bash
git add docs/demo.md
git commit -m "docs: cross-college demo script and verification results"
```

---

## Plan 3 验收

完成本计划后应满足：

- 三院课程共享：A 客户端可查看到 B/C 的 share='Y' 课程
- 跨院选课：A 学生可选 B/C 的共享课，选课记录写入目标院 DB
- 跨院退课：A 学生可退已选的跨院课
- 全局统计：管理员可查看三院汇总统计报表
- 10 个 XSL 文件在流程中实际发挥作用
- XSD 验证在数据交换边界生效
- `./scripts/start-all.sh` 一键启动 5 进程

**交付物：** 可运行代码 + Markdown 报告（`docs/report.md`）。
