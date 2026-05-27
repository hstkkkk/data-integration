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
- Modify: `college-a/src/main/java/college/a/server/handler/WithdrawLocalHandler.java` → 加跨院判定
- Create: `integration/src/main/java/integration/server/handler/CrossWithdrawHandler.java`
- Create: `college-a/src/main/java/college/a/server/handler/RevokeChoiceHandler.java`

与选课对称：跨院退课 → Integration → `REVOKE_CHOICE` → 目标院删除选课。

退课需要查选课记录的来源：如果 `origin` 不是本院 → 跨院退课流程。

- [ ] **Step 1: 在 WithdrawHandler 中加跨院路由**

修改 withdraw handler：根据 courseId 前缀判断。跨院课程 → 发 `CROSS_WITHDRAW` 给 Integration Server。

- [ ] **Step 2: 实现 CrossWithdrawHandler + RevokeChoiceHandler**

Integration Server 收到 `CROSS_WITHDRAW` → 路由到目标院 `REVOKE_CHOICE` → 目标院删除选课记录。

- [ ] **Step 3: commit**

---

## Task 8: 全局统计 — STATS_GLOBAL 流程

**Files:**
- Create: `integration/src/main/java/integration/server/handler/StatsGlobalHandler.java`
- Create: `college-a/src/main/java/college/a/server/handler/StatsPullHandler.java`（B/C 同理）

客户端发 `STATS_GLOBAL` → College Server 转发给 Integration Server → Integration 同时向三院发 `STATS_PULL` → 各院返回 `STATS_DATA`（本院格式的学生/课程/选课 XML）→ Integration 用 XSL 转换后聚合统计 → 返回统一格式统计报表。

- [ ] **Step 1: 实现 StatsPullHandler（各学院）**

查询本院全部数据，用本院 Adapter marshal 返回：

```java
// 伪代码
String studentsXml = StudentAdapter.marshal(studentDao.findAll());
String coursesXml = CourseAdapter.marshal(courseDao.findAll());
String choicesXml = ChoiceAdapter.marshal(choiceDao.findAll());
String combined = "<stats><students>" + studentsXml + "</students><courses>" + coursesXml + "</courses><choices>" + choicesXml + "</choices></stats>";
return Message.ok(req.requestId(), combined);
```

- [ ] **Step 2: 实现 StatsGlobalHandler（Integration Server）**

```java
// 向三院收集 STATS_DATA
// 聚合统计维度：
// - 各院学生数 / 课程数 / 共享课程数
// - 跨院选课总数与占比（根据 origin 字段）
// - 课程被选热度排行 Top 5
// 返回统一格式 XML
return Message.ok(req.requestId(), statsXml);
```

统计结果 XML 格式：
```xml
<stats>
  <summary>
    <totalStudents>150</totalStudents>
    <totalCourses>30</totalCourses>
    <totalSharedCourses>12</totalSharedCourses>
    <crossEnrollments>45</crossEnrollments>
    <crossPercentage>18</crossPercentage>
  </summary>
  <byCollege>
    <college code="A"><students>50</students><courses>10</courses><shared>4</shared></college>
    <college code="B"><students>50</students><courses>10</courses><shared>4</shared></college>
    <college code="C"><students>50</students><courses>10</courses><shared>4</shared></college>
  </byCollege>
  <topCourses>
    <course id="BC001" name="数据库原理" enrollments="28"/>
    <course id="AC001" name="数据库原理" enrollments="26"/>
    <!-- ... -->
  </topCourses>
</stats>
```

- [ ] **Step 3: commit**

---

## Task 9: 客户端支持共享课程和跨院选课/退课

**Files:**
- Modify: `client/src/main/java/client/ui/CourseListFrame.java` — 加"共享课程"刷新按钮
- Modify: `client/src/main/java/client/Main.java` — 支持 `--college=B|C` 的 XML 解析适配

客户端扩展：
1. CourseListFrame 增加"查看共享课程"按钮 → 发 `LIST_SHARED_COURSES`
2. 在课程列表中高亮显示跨院课程（来自其他学院的共享课程）
3. 选课时对跨院课程自动走 ENROLL（College Server 内部判断路由）

- [ ] **Step 1: 修改 CourseListFrame — 加共享课程 Tab/Button**

在现有界面加一个"刷新共享课程"按钮，发送 `LIST_SHARED_COURSES`。

- [ ] **Step 2: 修复三个学院的 XML 解析差异**

CourseListFrame 的 `populateTable` 需要根据 college 参数使用不同的字段名。Plan 2 已部分实现，做最后完善。

- [ ] **Step 3: commit**

---

## Task 10: Integration Server 端到端集成测试

**Files:**
- Create: `integration/src/test/java/integration/server/CrossCollegeIT.java`

启动三院 Server + Integration Server（在测试中），验证全链路：
1. A 客户端 LIST_SHARED_COURSES → 看到 B/C 的共享课程
2. A 学生选 B 课程 → B 的 DB 写入选课记录
3. A 学生退选 → B 的 DB 删除
4. STATS_GLOBAL 聚合正确

- [ ] **Step 1: 写端到端测试**

由于需要三院真实 DB（或 mock），测试设计为：用 mock CollegeClient 模拟各院响应。验证 StatsGlobalHandler 聚合逻辑正确性。

```java
@Test
void stats_aggregates_three_colleges() {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    // Setup mock responses with known student/course/choice counts
    // Assert aggregated stats match expected
}
```

Run: `mvn -pl integration -am test`

- [ ] **Step 2: commit**

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
