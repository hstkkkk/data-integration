# XML 异构数据集成 — Plan 1：基础设施 + 学院 A 垂直切片

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭起 Maven monorepo 与 common 公共基础设施（协议帧、XML 工具、统一格式 XSD），完成学院 A（SQL Server）端到端垂直切片：DAO、Server、最简 Swing 客户端。集成 Server 落地骨架（路由、ping）。结束时可演示 A 客户端登录、查本院课、选/退本院课。

**Architecture:** Maven 多模块 monorepo；Java 17 + DOM4J + Xerces + XSLT；纯 Socket 自定义文本帧承载 XML；学院 A 通过 JDBC 接 SQL Server（Docker 启动）。

**Tech Stack:** Java 17, Maven, DOM4J 2.1.x, xercesImpl 2.12.x, JUnit 5, Mockito, mssql-jdbc, Swing。

**前置条件：** 本地已装 JDK 17+ 和 Maven 3.8+；Docker 可用（用于 SQL Server）。

**参考：** [设计 spec](../specs/2026-05-26-xml-data-integration-design.md)

---

## Task 1: Maven 父 POM 与模块骨架

**Files:**
- Create: `pom.xml`（父）
- Create: `common/pom.xml`、`college-a/pom.xml`、`college-b/pom.xml`、`college-c/pom.xml`、`integration/pom.xml`、`client/pom.xml`
- Create: `.gitignore`

- [ ] **Step 1：写父 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>cn.edu.di</groupId>
  <artifactId>data-integration</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>common</module>
    <module>college-a</module>
    <module>college-b</module>
    <module>college-c</module>
    <module>integration</module>
    <module>client</module>
  </modules>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <dom4j.version>2.1.4</dom4j.version>
    <xerces.version>2.12.2</xerces.version>
    <junit.version>5.10.2</junit.version>
    <mockito.version>5.11.0</mockito.version>
    <mssql.version>12.6.1.jre11</mssql.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency><groupId>org.dom4j</groupId><artifactId>dom4j</artifactId><version>${dom4j.version}</version></dependency>
      <dependency><groupId>xerces</groupId><artifactId>xercesImpl</artifactId><version>${xerces.version}</version></dependency>
      <dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId><version>2.0.0</version></dependency>
      <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>${mssql.version}</version></dependency>
      <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>${junit.version}</version><scope>test</scope></dependency>
      <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId><version>${mockito.version}</version><scope>test</scope></dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version></plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

- [ ] **Step 2：写 common/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>cn.edu.di</groupId><artifactId>data-integration</artifactId><version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>common</artifactId>
  <dependencies>
    <dependency><groupId>org.dom4j</groupId><artifactId>dom4j</artifactId></dependency>
    <dependency><groupId>xerces</groupId><artifactId>xercesImpl</artifactId></dependency>
    <dependency><groupId>jaxen</groupId><artifactId>jaxen</artifactId></dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId></dependency>
  </dependencies>
</project>
```

- [ ] **Step 3：写 college-a/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>cn.edu.di</groupId><artifactId>data-integration</artifactId><version>1.0.0-SNAPSHOT</version>
  </parent>
  <artifactId>college-a</artifactId>
  <dependencies>
    <dependency><groupId>cn.edu.di</groupId><artifactId>common</artifactId><version>1.0.0-SNAPSHOT</version></dependency>
    <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId></dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId></dependency>
    <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId></dependency>
  </dependencies>
</project>
```

- [ ] **Step 4：college-b、college-c、integration、client 写占位 pom**

各自 artifactId 替换为模块名，dependencies 仅含 common + junit。Plan 2/3 时再加各自 JDBC 驱动。模板：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>cn.edu.di</groupId><artifactId>data-integration</artifactId><version>1.0.0-SNAPSHOT</version></parent>
  <artifactId>{MODULE_NAME}</artifactId>
  <dependencies>
    <dependency><groupId>cn.edu.di</groupId><artifactId>common</artifactId><version>1.0.0-SNAPSHOT</version></dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId></dependency>
  </dependencies>
</project>
```

- [ ] **Step 5：写 .gitignore**

```
target/
*.class
.idea/
*.iml
.vscode/
.DS_Store
logs/
*.log
data/
*.local.properties
```

- [ ] **Step 6：验证编译**

```bash
mvn -q -DskipTests compile
```

预期：BUILD SUCCESS。

- [ ] **Step 7：commit**

```bash
git add pom.xml common/pom.xml college-a/pom.xml college-b/pom.xml college-c/pom.xml integration/pom.xml client/pom.xml .gitignore
git commit -m "build: scaffold maven multi-module monorepo"
```

---

## Task 2: 消息帧编解码（MessageFrame，TDD）

**Files:**
- Create: `common/src/main/java/cn/edu/di/protocol/MessageFrame.java`
- Create: `common/src/main/java/cn/edu/di/protocol/ProtocolException.java`
- Test:   `common/src/test/java/cn/edu/di/protocol/MessageFrameTest.java`

帧格式（spec §5.1）：

```
<COMMAND> <REQUEST_ID>\n
Content-Length: <字节数>\n
\n
<UTF-8 XML 负载，恰好 N 字节>
```

- [ ] **Step 1：写失败测试**

```java
package cn.edu.di.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MessageFrameTest {

    @Test
    void encode_thenDecode_roundTrip() throws Exception {
        MessageFrame original = new MessageFrame("ENROLL", "req-001",
                "<enroll><sid>S1</sid><cid>C1</cid></enroll>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.writeTo(out);
        MessageFrame decoded = MessageFrame.readFrom(new ByteArrayInputStream(out.toByteArray()));
        assertEquals("ENROLL", decoded.command());
        assertEquals("req-001", decoded.requestId());
        assertEquals(original.payload(), decoded.payload());
    }

    @Test
    void emptyPayload_encodesContentLengthZero() throws Exception {
        MessageFrame f = new MessageFrame("PING", "r-1", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        f.writeTo(out);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Content-Length: 0"));
    }

    @Test
    void utf8PayloadLengthMeasuredInBytes() throws Exception {
        MessageFrame f = new MessageFrame("X", "r", "中");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        f.writeTo(out);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Content-Length: 3"));
    }

    @Test
    void malformedHeader_throwsProtocolException() {
        ByteArrayInputStream in = new ByteArrayInputStream("BADLINE\n\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(ProtocolException.class, () -> MessageFrame.readFrom(in));
    }

    @Test
    void contentLengthMismatch_throwsProtocolException() {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "X r\nContent-Length: 100\n\nshort".getBytes(StandardCharsets.UTF_8));
        assertThrows(ProtocolException.class, () -> MessageFrame.readFrom(in));
    }
}
```

- [ ] **Step 2：运行确认失败**

```bash
mvn -q -pl common test
```

- [ ] **Step 3：实现 ProtocolException 与 MessageFrame**

```java
// common/src/main/java/cn/edu/di/protocol/ProtocolException.java
package cn.edu.di.protocol;

public class ProtocolException extends RuntimeException {
    public ProtocolException(String msg) { super(msg); }
    public ProtocolException(String msg, Throwable cause) { super(msg, cause); }
}
```

```java
// common/src/main/java/cn/edu/di/protocol/MessageFrame.java
package cn.edu.di.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;

public record MessageFrame(String command, String requestId, String payload) {

    public void writeTo(OutputStream out) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        StringBuilder hdr = new StringBuilder()
                .append(command).append(' ').append(requestId).append('\n')
                .append("Content-Length: ").append(body.length).append('\n')
                .append('\n');
        out.write(hdr.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    public static MessageFrame readFrom(InputStream in) throws IOException {
        String firstLine = readLine(in);
        if (firstLine == null) throw new ProtocolException("EOF before header");
        String[] parts = firstLine.split(" ", 2);
        if (parts.length != 2) throw new ProtocolException("Bad header: " + firstLine);

        String clLine = readLine(in);
        if (clLine == null || !clLine.startsWith("Content-Length: "))
            throw new ProtocolException("Missing Content-Length");
        int len;
        try { len = Integer.parseInt(clLine.substring("Content-Length: ".length()).trim()); }
        catch (NumberFormatException e) { throw new ProtocolException("Bad Content-Length", e); }

        String blank = readLine(in);
        if (blank == null || !blank.isEmpty()) throw new ProtocolException("Missing blank line");

        byte[] body = in.readNBytes(len);
        if (body.length != len) throw new ProtocolException(
                "Truncated body: expected " + len + " got " + body.length);
        return new MessageFrame(parts[0], parts[1], new String(body, StandardCharsets.UTF_8));
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') return buf.toString(StandardCharsets.UTF_8);
            buf.write(b);
        }
        return buf.size() == 0 ? null : buf.toString(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4：运行测试**

```bash
mvn -q -pl common test
```

预期：5 个测试全 PASS。

- [ ] **Step 5：commit**

```bash
git add common/src
git commit -m "feat(common): message frame encoder/decoder with content-length and utf-8"
```

---

## Task 3: 命令枚举与 Message 抽象（TDD）

**Files:**
- Create: `common/src/main/java/cn/edu/di/protocol/Command.java`
- Create: `common/src/main/java/cn/edu/di/protocol/Message.java`
- Test:   `common/src/test/java/cn/edu/di/protocol/MessageTest.java`

- [ ] **Step 1：写失败测试**

```java
package cn.edu.di.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void parseFromFrame_setsCommandEnum() {
        Message m = Message.fromFrame(new MessageFrame("ENROLL", "r-1", "<x/>"));
        assertEquals(Command.ENROLL, m.command());
        assertEquals("r-1", m.requestId());
        assertEquals("<x/>", m.payload());
    }

    @Test
    void unknownCommand_yieldsCommandUnknown() {
        Message m = Message.fromFrame(new MessageFrame("FOOBAR", "r", ""));
        assertEquals(Command.UNKNOWN, m.command());
    }

    @Test
    void okResponse_factory() {
        Message m = Message.ok("r-9", "<result/>");
        assertEquals(Command.OK, m.command());
        assertEquals("<result/>", m.payload());
    }

    @Test
    void errResponse_carriesCodeAndDetail() {
        Message m = Message.err("r-9", "BUSINESS", "course full");
        assertEquals(Command.ERR, m.command());
        assertTrue(m.payload().contains("BUSINESS"));
        assertTrue(m.payload().contains("course full"));
    }

    @Test
    void readWrite_roundTrip() throws Exception {
        Message original = new Message(Command.ENROLL, "r-7", "<enroll/>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Message.write(out, original);
        Message decoded = Message.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(original, decoded);
    }
}
```

- [ ] **Step 2：运行确认失败**

```bash
mvn -q -pl common test -Dtest=MessageTest
```

- [ ] **Step 3：实现 Command 枚举**

```java
// common/src/main/java/cn/edu/di/protocol/Command.java
package cn.edu.di.protocol;

public enum Command {
    LOGIN, LIST_LOCAL_COURSES, LIST_SHARED_COURSES, ENROLL, WITHDRAW, STATS_GLOBAL,
    FETCH_SHARED_COURSES, ASK_COURSE_INFO, SEND_COURSE_INFO,
    CROSS_ENROLL, CROSS_WITHDRAW, APPLY_CHOICE, REVOKE_CHOICE,
    STATS_PULL, STATS_DATA,
    PING, OK, ERR, UNKNOWN;

    public static Command parse(String s) {
        try { return Command.valueOf(s); }
        catch (IllegalArgumentException e) { return UNKNOWN; }
    }
}
```

- [ ] **Step 4：实现 Message**

```java
// common/src/main/java/cn/edu/di/protocol/Message.java
package cn.edu.di.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public record Message(Command command, String requestId, String payload) {

    public static Message fromFrame(MessageFrame f) {
        return new Message(Command.parse(f.command()), f.requestId(), f.payload());
    }

    public MessageFrame toFrame() {
        return new MessageFrame(command.name(), requestId, payload);
    }

    /** Read a Message off the wire (decode frame + parse command). */
    public static Message read(InputStream in) throws IOException {
        return fromFrame(MessageFrame.readFrom(in));
    }

    /** Write a Message to the wire (encode frame). */
    public static void write(OutputStream out, Message m) throws IOException {
        m.toFrame().writeTo(out);
    }

    public static Message ok(String reqId, String payload) {
        return new Message(Command.OK, reqId, payload == null ? "" : payload);
    }

    public static Message err(String reqId, String code, String detail) {
        String xml = "<error><code>" + esc(code) + "</code><detail>" + esc(detail) + "</detail></error>";
        return new Message(Command.ERR, reqId, xml);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 5：运行测试**

```bash
mvn -q -pl common test
```

- [ ] **Step 6：commit**

```bash
git add common/src
git commit -m "feat(common): command enum and message wrapper with ok/err factories"
```

---

## Task 4: XML I/O（DOM4J 包装，TDD）

**Files:**
- Create: `common/src/main/java/cn/edu/di/xml/XmlIO.java`
- Create: `common/src/main/java/cn/edu/di/xml/XmlException.java`
- Test:   `common/src/test/java/cn/edu/di/xml/XmlIOTest.java`

- [ ] **Step 1：写失败测试**

```java
package cn.edu.di.xml;

import org.dom4j.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XmlIOTest {

    @Test
    void readsValidXmlIntoDocument() {
        Document doc = XmlIO.parse("<root><child id=\"1\"/></root>");
        assertEquals("root", doc.getRootElement().getName());
        assertEquals("1", doc.getRootElement().element("child").attributeValue("id"));
    }

    @Test
    void writesDocumentToString_pretty() {
        String out = XmlIO.toPrettyString(XmlIO.parse("<a><b>hi</b></a>"));
        assertTrue(out.contains("<a>"));
        assertTrue(out.contains("<b>hi</b>"));
        assertTrue(out.startsWith("<?xml"));
    }

    @Test
    void invalidXml_throwsXmlException() {
        assertThrows(XmlException.class, () -> XmlIO.parse("<broken"));
    }

    @Test
    void utf8RoundTrip() {
        String s = XmlIO.toPrettyString(XmlIO.parse("<n>张三</n>"));
        assertTrue(s.contains("张三"));
    }
}
```

- [ ] **Step 2：运行确认失败**

```bash
mvn -q -pl common test -Dtest=XmlIOTest
```

- [ ] **Step 3：实现**

```java
// common/src/main/java/cn/edu/di/xml/XmlException.java
package cn.edu.di.xml;

public class XmlException extends RuntimeException {
    public XmlException(String m, Throwable c) { super(m, c); }
    public XmlException(String m) { super(m); }
}
```

```java
// common/src/main/java/cn/edu/di/xml/XmlIO.java
package cn.edu.di.xml;

import org.dom4j.Document;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class XmlIO {

    private XmlIO() {}

    public static Document parse(String xml) {
        try { return new SAXReader().read(new StringReader(xml)); }
        catch (Exception e) { throw new XmlException("parse failed: " + e.getMessage(), e); }
    }

    public static Document parse(InputStream in) {
        try { return new SAXReader().read(new InputStreamReader(in, StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new XmlException("parse failed: " + e.getMessage(), e); }
    }

    public static String toPrettyString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            OutputFormat fmt = OutputFormat.createPrettyPrint();
            fmt.setEncoding("UTF-8");
            XMLWriter xw = new XMLWriter(sw, fmt);
            xw.write(doc);
            xw.flush();
            return sw.toString();
        } catch (IOException e) { throw new XmlException("write failed", e); }
    }
}
```

- [ ] **Step 4：运行测试 + commit**

```bash
mvn -q -pl common test
git add common/src
git commit -m "feat(common): xml read/write utility around dom4j"
```

---

## Task 5: XSD 验证器（Xerces，TDD）

**Files:**
- Create: `common/src/main/java/cn/edu/di/xml/XsdValidator.java`
- Create: `common/src/test/resources/test-schemas/sample.xsd`
- Test:   `common/src/test/java/cn/edu/di/xml/XsdValidatorTest.java`

- [ ] **Step 1：写测试用 sample.xsd**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="person">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="name" type="xs:string"/>
        <xs:element name="age" type="xs:positiveInteger"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

- [ ] **Step 2：写失败测试**

```java
package cn.edu.di.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XsdValidatorTest {
    private final XsdValidator v = XsdValidator.fromClasspath("/test-schemas/sample.xsd");

    @Test
    void validXml_returnsValid() {
        var r = v.validate("<person><name>X</name><age>20</age></person>");
        assertTrue(r.valid(), r.toString());
    }

    @Test
    void wrongElement_returnsInvalid() {
        var r = v.validate("<animal><kind>cat</kind></animal>");
        assertFalse(r.valid());
        assertFalse(r.errors().isEmpty());
    }

    @Test
    void negativeAge_invalid() {
        var r = v.validate("<person><name>X</name><age>-5</age></person>");
        assertFalse(r.valid());
    }
}
```

- [ ] **Step 3：运行确认失败**

```bash
mvn -q -pl common test -Dtest=XsdValidatorTest
```

- [ ] **Step 4：实现**

```java
// common/src/main/java/cn/edu/di/xml/XsdValidator.java
package cn.edu.di.xml;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XsdValidator {

    private final Schema schema;
    private XsdValidator(Schema s) { this.schema = s; }

    public static XsdValidator fromClasspath(String path) {
        try (InputStream in = XsdValidator.class.getResourceAsStream(path)) {
            if (in == null) throw new XmlException("xsd not found: " + path);
            byte[] bytes = in.readAllBytes();
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return new XsdValidator(sf.newSchema(new StreamSource(new ByteArrayInputStream(bytes))));
        } catch (Exception e) { throw new XmlException("load xsd: " + path, e); }
    }

    public Result validate(String xml) {
        Validator v = schema.newValidator();
        List<String> errs = new ArrayList<>();
        v.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) { errs.add("WARN " + e.getMessage()); }
            public void error(SAXParseException e) { errs.add("ERROR " + e.getMessage()); }
            public void fatalError(SAXParseException e) { errs.add("FATAL " + e.getMessage()); }
        });
        try {
            v.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) { errs.add("EXCEPTION " + e.getMessage()); }
        return new Result(errs.isEmpty(), Collections.unmodifiableList(errs));
    }

    public record Result(boolean valid, List<String> errors) {
        @Override public String toString() {
            return valid ? "valid" : "invalid: " + String.join("; ", errors);
        }
    }
}
```

- [ ] **Step 5：运行 + commit**

```bash
mvn -q -pl common test
git add common/src
git commit -m "feat(common): xsd validator with error collection (xerces)"
```

---

## Task 6: XSLT 转换器（TrAX，TDD）

**Files:**
- Create: `common/src/main/java/cn/edu/di/xml/XsltTransformer.java`
- Create: `common/src/test/resources/test-xsl/identity.xsl`
- Create: `common/src/test/resources/test-xsl/uppercase-name.xsl`
- Test:   `common/src/test/java/cn/edu/di/xml/XsltTransformerTest.java`

- [ ] **Step 1：写测试用 XSL**

```xml
<!-- identity.xsl -->
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8"/>
  <xsl:template match="@*|node()">
    <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
  </xsl:template>
</xsl:stylesheet>
```

```xml
<!-- uppercase-name.xsl -->
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8"/>
  <xsl:template match="name">
    <name><xsl:value-of select="translate(., 'abcdefghijklmnopqrstuvwxyz',
                                              'ABCDEFGHIJKLMNOPQRSTUVWXYZ')"/></name>
  </xsl:template>
  <xsl:template match="@*|node()">
    <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
  </xsl:template>
</xsl:stylesheet>
```

- [ ] **Step 2：写失败测试**

```java
package cn.edu.di.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XsltTransformerTest {

    @Test
    void identityXsl_returnsSameStructure() {
        String out = XsltTransformer.fromClasspath("/test-xsl/identity.xsl")
                                    .transform("<a><b>x</b></a>");
        assertTrue(out.contains("<a>"));
        assertTrue(out.contains("<b>x</b>"));
    }

    @Test
    void appliesActualTransform() {
        String out = XsltTransformer.fromClasspath("/test-xsl/uppercase-name.xsl")
                                    .transform("<root><name>alice</name><name>bob</name></root>");
        assertTrue(out.contains("<name>ALICE</name>"));
        assertTrue(out.contains("<name>BOB</name>"));
    }
}
```

- [ ] **Step 3：运行确认失败**

```bash
mvn -q -pl common test -Dtest=XsltTransformerTest
```

- [ ] **Step 4：实现**

```java
// common/src/main/java/cn/edu/di/xml/XsltTransformer.java
package cn.edu.di.xml;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class XsltTransformer {

    private final byte[] xslBytes;
    private XsltTransformer(byte[] b) { this.xslBytes = b; }

    public static XsltTransformer fromClasspath(String path) {
        try (InputStream in = XsltTransformer.class.getResourceAsStream(path)) {
            if (in == null) throw new XmlException("xsl not found: " + path);
            return new XsltTransformer(in.readAllBytes());
        } catch (Exception e) { throw new XmlException("load xsl: " + path, e); }
    }

    public String transform(String xml) {
        try {
            Transformer t = TransformerFactory.newInstance()
                    .newTransformer(new StreamSource(new ByteArrayInputStream(xslBytes)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transform(
                    new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))),
                    new StreamResult(out));
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) { throw new XmlException("transform: " + e.getMessage(), e); }
    }
}
```

- [ ] **Step 5：运行 + commit**

```bash
mvn -q -pl common test
git add common/src
git commit -m "feat(common): xslt transformer wrapper around trax"
```

---

## Task 7: 统一格式 XSD（formatStudent / formatClass / formatChoice）

**Files:**
- Create: `common/src/main/resources/schema/formatClass.xsd`
- Create: `common/src/main/resources/schema/formatStudent.xsd`
- Create: `common/src/main/resources/schema/formatChoice.xsd`
- Test:   `common/src/test/java/cn/edu/di/xml/UnifiedSchemasTest.java`

- [ ] **Step 1：写 formatClass.xsd**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="classes">
    <xs:complexType><xs:sequence>
      <xs:element name="class" minOccurs="1" maxOccurs="unbounded">
        <xs:complexType><xs:sequence>
          <xs:element name="id"       type="xs:string"/>
          <xs:element name="name"     type="xs:string"/>
          <xs:element name="time"     type="xs:unsignedByte"/>
          <xs:element name="score"    type="xs:unsignedByte"/>
          <xs:element name="teacher"  type="xs:string"/>
          <xs:element name="location" type="xs:string"/>
          <xs:element name="share">
            <xs:simpleType><xs:restriction base="xs:string">
              <xs:enumeration value="Y"/><xs:enumeration value="N"/>
            </xs:restriction></xs:simpleType>
          </xs:element>
          <xs:element name="origin">
            <xs:simpleType><xs:restriction base="xs:string">
              <xs:enumeration value="A"/><xs:enumeration value="B"/><xs:enumeration value="C"/>
            </xs:restriction></xs:simpleType>
          </xs:element>
        </xs:sequence></xs:complexType>
      </xs:element>
    </xs:sequence></xs:complexType>
  </xs:element>
</xs:schema>
```

- [ ] **Step 2：写 formatStudent.xsd**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="students">
    <xs:complexType><xs:sequence>
      <xs:element name="student" minOccurs="1" maxOccurs="unbounded">
        <xs:complexType><xs:sequence>
          <xs:element name="id"     type="xs:string"/>
          <xs:element name="name"   type="xs:string"/>
          <xs:element name="sex"    type="xs:string"/>
          <xs:element name="major"  type="xs:string"/>
          <xs:element name="origin">
            <xs:simpleType><xs:restriction base="xs:string">
              <xs:enumeration value="A"/><xs:enumeration value="B"/><xs:enumeration value="C"/>
            </xs:restriction></xs:simpleType>
          </xs:element>
        </xs:sequence></xs:complexType>
      </xs:element>
    </xs:sequence></xs:complexType>
  </xs:element>
</xs:schema>
```

- [ ] **Step 3：写 formatChoice.xsd**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="choices">
    <xs:complexType><xs:sequence>
      <xs:element name="choice" minOccurs="1" maxOccurs="unbounded">
        <xs:complexType><xs:sequence>
          <xs:element name="sid"   type="xs:string"/>
          <xs:element name="cid"   type="xs:string"/>
          <xs:element name="score" type="xs:unsignedByte" minOccurs="0"/>
          <xs:element name="originStudent">
            <xs:simpleType><xs:restriction base="xs:string">
              <xs:enumeration value="A"/><xs:enumeration value="B"/><xs:enumeration value="C"/>
            </xs:restriction></xs:simpleType>
          </xs:element>
          <xs:element name="originCourse">
            <xs:simpleType><xs:restriction base="xs:string">
              <xs:enumeration value="A"/><xs:enumeration value="B"/><xs:enumeration value="C"/>
            </xs:restriction></xs:simpleType>
          </xs:element>
        </xs:sequence></xs:complexType>
      </xs:element>
    </xs:sequence></xs:complexType>
  </xs:element>
</xs:schema>
```

- [ ] **Step 4：写测试**

```java
package cn.edu.di.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedSchemasTest {

    @Test
    void validClass_passes() {
        var v = XsdValidator.fromClasspath("/schema/formatClass.xsd");
        assertTrue(v.validate(
            "<classes><class>" +
            "<id>A001</id><name>DB</name><time>40</time><score>3</score>" +
            "<teacher>Li</teacher><location>R1</location><share>Y</share><origin>A</origin>" +
            "</class></classes>").valid());
    }

    @Test
    void classMissingShare_fails() {
        var v = XsdValidator.fromClasspath("/schema/formatClass.xsd");
        assertFalse(v.validate(
            "<classes><class><id>A001</id><name>DB</name><time>40</time>" +
            "<score>3</score><teacher>Li</teacher><location>R1</location><origin>A</origin>" +
            "</class></classes>").valid());
    }

    @Test
    void validStudent_passes() {
        var v = XsdValidator.fromClasspath("/schema/formatStudent.xsd");
        assertTrue(v.validate(
            "<students><student>" +
            "<id>S1</id><name>张三</name><sex>男</sex><major>CS</major><origin>A</origin>" +
            "</student></students>").valid());
    }

    @Test
    void choiceWithBadOrigin_fails() {
        var v = XsdValidator.fromClasspath("/schema/formatChoice.xsd");
        assertFalse(v.validate(
            "<choices><choice><sid>S1</sid><cid>C1</cid>" +
            "<originStudent>A</originStudent><originCourse>Z</originCourse>" +
            "</choice></choices>").valid());
    }
}
```

- [ ] **Step 5：运行 + commit**

```bash
mvn -q -pl common test
git add common/src
git commit -m "feat(common): unified-format xsds for class/student/choice"
```

---

## Task 8: SQL Server 启动脚本与 init_a.sql

**Files:**
- Create: `scripts/db/start-sqlserver.sh`
- Create: `college-a/src/main/resources/sql/init_a.sql`
- Create: `college-a/src/main/resources/db.properties`

- [ ] **Step 1：写 SQL Server Docker 启动脚本**

```bash
#!/usr/bin/env bash
# scripts/db/start-sqlserver.sh
set -e
NAME=di-sqlserver
PASSWORD='Di_Strong_Pwd!2024'

if docker ps -a --format '{{.Names}}' | grep -q "^${NAME}$"; then
  docker start "${NAME}"
else
  docker run -e "ACCEPT_EULA=Y" -e "MSSQL_SA_PASSWORD=${PASSWORD}" \
    -p 1433:1433 --name "${NAME}" \
    -d mcr.microsoft.com/mssql/server:2022-latest
fi

echo "Waiting for SQL Server..."
for i in {1..30}; do
  docker exec "${NAME}" /opt/mssql-tools18/bin/sqlcmd -S localhost \
       -U SA -P "${PASSWORD}" -No -Q "SELECT 1" >/dev/null 2>&1 && break
  sleep 2
done
echo "SQL Server up at localhost:1433 (SA / ${PASSWORD})"
```

```bash
chmod +x scripts/db/start-sqlserver.sh
```

- [ ] **Step 2：写 db.properties**

```properties
# college-a/src/main/resources/db.properties
jdbc.url=jdbc:sqlserver://localhost:1433;databaseName=collegeA;encrypt=false
jdbc.user=SA
jdbc.password=Di_Strong_Pwd!2024
```

- [ ] **Step 3：写 init_a.sql**

```sql
-- college-a/src/main/resources/sql/init_a.sql
IF DB_ID('collegeA') IS NULL CREATE DATABASE collegeA;
GO
USE collegeA;
GO

IF OBJECT_ID('选课', 'U') IS NOT NULL DROP TABLE 选课;
IF OBJECT_ID('学生', 'U') IS NOT NULL DROP TABLE 学生;
IF OBJECT_ID('课程', 'U') IS NOT NULL DROP TABLE 课程;
IF OBJECT_ID('账户', 'U') IS NOT NULL DROP TABLE 账户;

CREATE TABLE 账户 (
  账户名 varchar(10) NOT NULL PRIMARY KEY,
  密码   varchar(6)  NOT NULL,
  权限   char(4)     NOT NULL
);

CREATE TABLE 学生 (
  学号     varchar(12) NOT NULL PRIMARY KEY,
  姓名     varchar(10) NOT NULL,
  性别     varchar(2)  NOT NULL,
  院系     varchar(10) NOT NULL,
  关联账户 varchar(10) NOT NULL FOREIGN KEY REFERENCES 账户(账户名)
);

CREATE TABLE 课程 (
  课程编号 varchar(8)  NOT NULL PRIMARY KEY,
  课程名称 varchar(20) NOT NULL,
  学分     varchar(2)  NOT NULL,
  授课老师 varchar(10) NOT NULL,
  授课地点 varchar(20) NOT NULL,
  共享     char(1)     NOT NULL,
  课时     int         NOT NULL DEFAULT 32
);

CREATE TABLE 选课 (
  课程编号  varchar(8)  NOT NULL FOREIGN KEY REFERENCES 课程(课程编号),
  学生编号  varchar(12) NOT NULL FOREIGN KEY REFERENCES 学生(学号),
  成绩      varchar(3)  NULL,
  来源      char(1)     NOT NULL DEFAULT 'A',
  CONSTRAINT UK_选课 UNIQUE (课程编号, 学生编号)
);
GO
```

> 在课件原表上加了 `课时`（学生看课程时需要）和选课表的 `来源`（跨院选课副本标记，Plan 2 用）。

- [ ] **Step 4：执行建库**

```bash
./scripts/db/start-sqlserver.sh
docker exec -i di-sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U SA -P 'Di_Strong_Pwd!2024' -No \
  < college-a/src/main/resources/sql/init_a.sql
```

预期：脚本执行无报错。

- [ ] **Step 5：commit**

```bash
git add scripts/db/start-sqlserver.sh college-a/src/main/resources/sql/init_a.sql college-a/src/main/resources/db.properties
git commit -m "build(college-a): sql server docker script and init schema"
```

---

### Task 9：College A 本地 XSD（学生/课程/选课）

**Files:**
- Create: `college-a/src/main/resources/schema/studentA.xsd`
- Create: `college-a/src/main/resources/schema/classA.xsd`
- Create: `college-a/src/main/resources/schema/choiceA.xsd`
- Test: `college-a/src/test/java/college/a/xml/SchemaATest.java`

- [ ] **Step 1：写失败测试**

```java
// college-a/src/test/java/college/a/xml/SchemaATest.java
package college.a.xml;

import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemaATest {
  XsdValidator load(String name) {
    return XsdValidator.fromClasspath("/schema/" + name);
  }

  @Test
  void student_valid() {
    String xml = """
      <学生集>
        <学生><学号>AS001</学号><姓名>张三</姓名><性别>男</性别><院系>计算机</院系><关联账户>as001</关联账户></学生>
      </学生集>""";
    var r = load("studentA.xsd").validate(xml);
    assertTrue(r.valid(), r.errors().toString());
  }

  @Test
  void student_missing_field_invalid() {
    String xml = "<学生集><学生><学号>AS001</学号></学生></学生集>";
    assertFalse(load("studentA.xsd").validate(xml).valid());
  }

  @Test
  void class_valid() {
    String xml = """
      <课程集>
        <课程><课程编号>AC001</课程编号><课程名称>数据库</课程名称><学分>3</学分>
              <授课老师>李老师</授课老师><授课地点>A101</授课地点><共享>Y</共享></课程>
      </课程集>""";
    assertTrue(load("classA.xsd").validate(xml).valid());
  }

  @Test
  void choice_valid() {
    String xml = """
      <选课集>
        <选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号><成绩>90</成绩></选课>
      </选课集>""";
    assertTrue(load("choiceA.xsd").validate(xml).valid());
  }
}
```

- [ ] **Step 2：运行确认 FAIL**

Run: `mvn -pl college-a -am test -Dtest=SchemaATest`
Expected：找不到 schema 文件而失败。

- [ ] **Step 3：写 XSD（A 学院字段命名贴近课件 P74，中文标签）**

`college-a/src/main/resources/schema/studentA.xsd`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
  <xs:element name="学生集">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="学生" minOccurs="0" maxOccurs="unbounded">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="学号" type="xs:string"/>
              <xs:element name="姓名" type="xs:string"/>
              <xs:element name="性别">
                <xs:simpleType>
                  <xs:restriction base="xs:string">
                    <xs:enumeration value="男"/>
                    <xs:enumeration value="女"/>
                  </xs:restriction>
                </xs:simpleType>
              </xs:element>
              <xs:element name="院系" type="xs:string"/>
              <xs:element name="关联账户" type="xs:string" minOccurs="0"/>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

`college-a/src/main/resources/schema/classA.xsd`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
  <xs:element name="课程集">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="课程" minOccurs="0" maxOccurs="unbounded">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="课程编号" type="xs:string"/>
              <xs:element name="课程名称" type="xs:string"/>
              <xs:element name="课时" type="xs:integer" minOccurs="0"/>
              <xs:element name="学分" type="xs:decimal"/>
              <xs:element name="授课老师" type="xs:string"/>
              <xs:element name="授课地点" type="xs:string"/>
              <xs:element name="共享">
                <xs:simpleType>
                  <xs:restriction base="xs:string">
                    <xs:enumeration value="Y"/>
                    <xs:enumeration value="N"/>
                  </xs:restriction>
                </xs:simpleType>
              </xs:element>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

`college-a/src/main/resources/schema/choiceA.xsd`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
  <xs:element name="选课集">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="选课" minOccurs="0" maxOccurs="unbounded">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="课程编号" type="xs:string"/>
              <xs:element name="学生编号" type="xs:string"/>
              <xs:element name="成绩" type="xs:string" minOccurs="0"/>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

- [ ] **Step 4：运行 PASS**

Run: `mvn -pl college-a -am test -Dtest=SchemaATest`
Expected：4 个测试全过。

- [ ] **Step 5：commit**

```bash
git add college-a/src/main/resources/schema/ college-a/src/test/java/college/a/xml/SchemaATest.java
git commit -m "feat(college-a): local xsds for student/class/choice (chinese tags)"
```

---

### Task 10：JdbcFactory + AccountDao + 登录服务

**Files:**
- Create: `college-a/src/main/java/college/a/jdbc/JdbcFactory.java`
- Create: `college-a/src/main/java/college/a/dao/AccountDao.java`
- Create: `college-a/src/main/java/college/a/service/AuthService.java`
- Test: `college-a/src/test/java/college/a/dao/AccountDaoIT.java`
- Test: `college-a/src/test/java/college/a/service/AuthServiceTest.java`

> AccountDao 走真实 SQL Server（集成测试，需先 `start-sqlserver.sh` 并执行 init_a.sql）；AuthService 用 mock 做单元测试。

- [ ] **Step 1：写 AccountDao 集成测试（FAIL）**

```java
// college-a/src/test/java/college/a/dao/AccountDaoIT.java
package college.a.dao;

import college.a.jdbc.JdbcFactory;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountDaoIT {
  static AccountDao dao;

  @BeforeAll
  static void setup() throws Exception {
    var ds = JdbcFactory.fromClasspath("/db.properties");
    dao = new AccountDao(ds);
    try (var c = ds.getConnection(); var st = c.createStatement()) {
      st.execute("DELETE FROM 账户 WHERE 账户名='unit_test'");
      st.execute("INSERT INTO 账户(账户名,密码,权限) VALUES('unit_test','pwd123','stu')");
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 账户 WHERE 账户名='unit_test'");
    }
  }

  @Test @Order(1)
  void findByUsername_returns_account() {
    var acc = dao.findByUsername("unit_test").orElseThrow();
    assertEquals("pwd123", acc.password());
    assertEquals("stu", acc.role());
  }

  @Test @Order(2)
  void findByUsername_unknown_returns_empty() {
    assertTrue(dao.findByUsername("__nope__").isEmpty());
  }
}
```

- [ ] **Step 2：运行确认 FAIL**

Run: `mvn -pl college-a -am test -Dtest=AccountDaoIT`
Expected：编译失败（JdbcFactory / AccountDao 不存在）。

- [ ] **Step 3：实现 JdbcFactory**

```java
// college-a/src/main/java/college/a/jdbc/JdbcFactory.java
package college.a.jdbc;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public final class JdbcFactory {
  private JdbcFactory() {}

  public static DataSource fromClasspath(String resource) throws IOException {
    Properties p = new Properties();
    try (var in = JdbcFactory.class.getResourceAsStream(resource)) {
      if (in == null) throw new IOException("resource not found: " + resource);
      p.load(in);
    }
    String url = p.getProperty("jdbc.url");
    String user = p.getProperty("jdbc.user");
    String pass = p.getProperty("jdbc.password");
    return new SimpleDataSource(url, user, pass);
  }

  private record SimpleDataSource(String url, String user, String pass) implements DataSource {
    @Override public Connection getConnection() throws java.sql.SQLException {
      return DriverManager.getConnection(url, user, pass);
    }
    @Override public Connection getConnection(String u, String p) throws java.sql.SQLException {
      return DriverManager.getConnection(url, u, p);
    }
    @Override public java.io.PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(java.io.PrintWriter out) {}
    @Override public void setLoginTimeout(int seconds) {}
    @Override public int getLoginTimeout() { return 0; }
    @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getLogger("jdbc"); }
    @Override public <T> T unwrap(Class<T> i) { throw new UnsupportedOperationException(); }
    @Override public boolean isWrapperFor(Class<?> i) { return false; }
  }
}
```

- [ ] **Step 4：实现 AccountDao**

```java
// college-a/src/main/java/college/a/dao/AccountDao.java
package college.a.dao;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;

public class AccountDao {
  public record Account(String username, String password, String role) {}

  private final DataSource ds;

  public AccountDao(DataSource ds) { this.ds = ds; }

  public Optional<Account> findByUsername(String username) {
    String sql = "SELECT 账户名,密码,权限 FROM 账户 WHERE 账户名 = ?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, username);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(new Account(rs.getString(1), rs.getString(2), rs.getString(3)));
      }
    } catch (SQLException e) {
      throw new RuntimeException("query account failed", e);
    }
  }
}
```

- [ ] **Step 5：运行 IT，确认 PASS**

Run: `mvn -pl college-a -am test -Dtest=AccountDaoIT`
Expected：2 个测试通过。前提：SQL Server 容器运行中且 init_a.sql 已执行。

- [ ] **Step 6：写 AuthService 单测（FAIL）**

```java
// college-a/src/test/java/college/a/service/AuthServiceTest.java
package college.a.service;

import college.a.dao.AccountDao;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
  @Test
  void login_success() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(
        Optional.of(new AccountDao.Account("u", "p", "stu")));
    var svc = new AuthService(dao);
    var s = svc.login("u", "p").orElseThrow();
    assertEquals("u", s.username());
    assertEquals("stu", s.role());
  }

  @Test
  void login_wrong_password_empty() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(
        Optional.of(new AccountDao.Account("u", "p", "stu")));
    assertTrue(new AuthService(dao).login("u", "x").isEmpty());
  }

  @Test
  void login_unknown_empty() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("x")).thenReturn(Optional.empty());
    assertTrue(new AuthService(dao).login("x", "p").isEmpty());
  }
}
```

加入 Mockito 依赖到父 pom（如未加）：

```xml
<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <version>5.11.0</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 7：实现 AuthService**

```java
// college-a/src/main/java/college/a/service/AuthService.java
package college.a.service;

import college.a.dao.AccountDao;
import java.util.Optional;

public class AuthService {
  public record Session(String username, String role) {}

  private final AccountDao dao;

  public AuthService(AccountDao dao) { this.dao = dao; }

  public Optional<Session> login(String user, String pass) {
    return dao.findByUsername(user)
        .filter(a -> a.password().equals(pass))
        .map(a -> new Session(a.username(), a.role()));
  }
}
```

- [ ] **Step 8：运行 PASS**

Run: `mvn -pl college-a -am test -Dtest=AuthServiceTest`

- [ ] **Step 9：commit**

```bash
git add college-a/src/main/java college-a/src/test/java/college/a pom.xml
git commit -m "feat(college-a): jdbc factory, account dao and auth service"
```

---

### Task 11：StudentDao（A 院）

**Files:**
- Create: `college-a/src/main/java/college/a/dao/StudentDao.java`
- Test: `college-a/src/test/java/college/a/dao/StudentDaoIT.java`

- [ ] **Step 1：写失败测试**

```java
// college-a/src/test/java/college/a/dao/StudentDaoIT.java
package college.a.dao;

import college.a.jdbc.JdbcFactory;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudentDaoIT {
  static StudentDao dao;

  @BeforeAll
  static void setup() throws Exception {
    dao = new StudentDao(JdbcFactory.fromClasspath("/db.properties"));
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 学生 WHERE 学号 LIKE 'IT_%'");
      st.execute("INSERT INTO 学生(学号,姓名,性别,院系,关联账户) VALUES('IT_001','测试甲','男','计算机',NULL)");
      st.execute("INSERT INTO 学生(学号,姓名,性别,院系,关联账户) VALUES('IT_002','测试乙','女','计算机',NULL)");
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 学生 WHERE 学号 LIKE 'IT_%'");
    }
  }

  @Test @Order(1)
  void findById_present() {
    var s = dao.findById("IT_001").orElseThrow();
    assertEquals("测试甲", s.name());
  }

  @Test @Order(2)
  void findAll_includes_inserted() {
    List<StudentDao.Row> all = dao.findAll();
    assertTrue(all.stream().anyMatch(r -> r.id().equals("IT_002")));
  }

  @Test @Order(3)
  void insertIfMissing_inserts_once() {
    boolean first = dao.insertIfMissing(new StudentDao.Row("IT_003", "测试丙", "男", "计算机", null));
    boolean second = dao.insertIfMissing(new StudentDao.Row("IT_003", "测试丙", "男", "计算机", null));
    assertTrue(first);
    assertFalse(second);
  }
}
```

- [ ] **Step 2：运行 FAIL**

Run: `mvn -pl college-a -am test -Dtest=StudentDaoIT`

- [ ] **Step 3：实现**

```java
// college-a/src/main/java/college/a/dao/StudentDao.java
package college.a.dao;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentDao {
  public record Row(String id, String name, String sex, String dept, String accountRef) {}

  private final DataSource ds;

  public StudentDao(DataSource ds) { this.ds = ds; }

  public Optional<Row> findById(String id) {
    String sql = "SELECT 学号,姓名,性别,院系,关联账户 FROM 学生 WHERE 学号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(map(rs));
      }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findAll() {
    String sql = "SELECT 学号,姓名,性别,院系,关联账户 FROM 学生";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(map(rs));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public boolean insertIfMissing(Row r) {
    String sql = "INSERT INTO 学生(学号,姓名,性别,院系,关联账户) " +
                 "SELECT ?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM 学生 WHERE 学号=?)";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, r.id()); ps.setString(2, r.name());
      ps.setString(3, r.sex()); ps.setString(4, r.dept());
      ps.setString(5, r.accountRef()); ps.setString(6, r.id());
      return ps.executeUpdate() == 1;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  private static Row map(java.sql.ResultSet rs) throws SQLException {
    return new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
  }
}
```

- [ ] **Step 4：运行 PASS**

- [ ] **Step 5：commit**

```bash
git add college-a/src/main/java/college/a/dao/StudentDao.java college-a/src/test/java/college/a/dao/StudentDaoIT.java
git commit -m "feat(college-a): student dao with findById/findAll/insertIfMissing"
```

---

### Task 12：CourseDao（A 院）

**Files:**
- Create: `college-a/src/main/java/college/a/dao/CourseDao.java`
- Test: `college-a/src/test/java/college/a/dao/CourseDaoIT.java`

- [ ] **Step 1：写失败测试**

```java
// college-a/src/test/java/college/a/dao/CourseDaoIT.java
package college.a.dao;

import college.a.jdbc.JdbcFactory;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CourseDaoIT {
  static CourseDao dao;

  @BeforeAll
  static void setup() throws Exception {
    dao = new CourseDao(JdbcFactory.fromClasspath("/db.properties"));
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 课程 WHERE 课程编号 LIKE 'IT_%'");
      st.execute("INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) " +
                 "VALUES('IT_C01','测试课甲',32,2.0,'李老师','A101','Y')");
      st.execute("INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) " +
                 "VALUES('IT_C02','测试课乙',16,1.0,'王老师','A102','N')");
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 课程 WHERE 课程编号 LIKE 'IT_%'");
    }
  }

  @Test @Order(1)
  void findById() {
    var c = dao.findById("IT_C01").orElseThrow();
    assertEquals("测试课甲", c.name());
    assertEquals(0, BigDecimal.valueOf(2.0).compareTo(c.score()));
    assertTrue(c.shared());
  }

  @Test @Order(2)
  void findShared_only_Y() {
    List<CourseDao.Row> shared = dao.findShared();
    assertTrue(shared.stream().anyMatch(r -> r.id().equals("IT_C01")));
    assertTrue(shared.stream().noneMatch(r -> r.id().equals("IT_C02")));
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl college-a -am test -Dtest=CourseDaoIT`

- [ ] **Step 3：实现**

```java
// college-a/src/main/java/college/a/dao/CourseDao.java
package college.a.dao;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CourseDao {
  public record Row(String id, String name, int hours, BigDecimal score,
                    String teacher, String location, boolean shared) {}

  private final DataSource ds;

  public CourseDao(DataSource ds) { this.ds = ds; }

  public Optional<Row> findById(String id) {
    String sql = "SELECT 课程编号,课程名称,课时,学分,授课老师,授课地点,共享 FROM 课程 WHERE 课程编号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(map(rs));
      }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findAll() {
    return query("SELECT 课程编号,课程名称,课时,学分,授课老师,授课地点,共享 FROM 课程");
  }

  public List<Row> findShared() {
    return query("SELECT 课程编号,课程名称,课时,学分,授课老师,授课地点,共享 FROM 课程 WHERE 共享='Y'");
  }

  private List<Row> query(String sql) {
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(map(rs));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  private static Row map(java.sql.ResultSet rs) throws SQLException {
    return new Row(rs.getString(1), rs.getString(2), rs.getInt(3),
                   rs.getBigDecimal(4), rs.getString(5), rs.getString(6),
                   "Y".equalsIgnoreCase(rs.getString(7)));
  }
}
```

- [ ] **Step 4：PASS**

- [ ] **Step 5：commit**

```bash
git add college-a/src/main/java/college/a/dao/CourseDao.java college-a/src/test/java/college/a/dao/CourseDaoIT.java
git commit -m "feat(college-a): course dao with findById/findAll/findShared"
```

---

### Task 13：ChoiceDao（A 院）

**Files:**
- Create: `college-a/src/main/java/college/a/dao/ChoiceDao.java`
- Test: `college-a/src/test/java/college/a/dao/ChoiceDaoIT.java`

- [ ] **Step 1：写失败测试**

```java
// college-a/src/test/java/college/a/dao/ChoiceDaoIT.java
package college.a.dao;

import college.a.jdbc.JdbcFactory;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChoiceDaoIT {
  static ChoiceDao dao;

  @BeforeAll
  static void setup() throws Exception {
    dao = new ChoiceDao(JdbcFactory.fromClasspath("/db.properties"));
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 选课  WHERE 学生编号='IT_S' OR 课程编号='IT_C'");
      st.execute("DELETE FROM 学生  WHERE 学号='IT_S'");
      st.execute("DELETE FROM 课程  WHERE 课程编号='IT_C'");
      st.execute("INSERT INTO 学生(学号,姓名,性别,院系,关联账户) VALUES('IT_S','测试','男','计算机',NULL)");
      st.execute("INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) " +
                 "VALUES('IT_C','测试课',16,1.0,'X','A101','N')");
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 选课 WHERE 学生编号='IT_S' OR 课程编号='IT_C'");
      st.execute("DELETE FROM 学生 WHERE 学号='IT_S'");
      st.execute("DELETE FROM 课程 WHERE 课程编号='IT_C'");
    }
  }

  @Test @Order(1)
  void enroll_local_inserts() {
    int rows = dao.enrollLocal("IT_S", "IT_C");
    assertEquals(1, rows);
    assertTrue(dao.exists("IT_S", "IT_C"));
  }

  @Test @Order(2)
  void enroll_duplicate_throws() {
    assertThrows(RuntimeException.class, () -> dao.enrollLocal("IT_S", "IT_C"));
  }

  @Test @Order(3)
  void withdraw_removes() {
    int rows = dao.withdraw("IT_S", "IT_C");
    assertEquals(1, rows);
    assertFalse(dao.exists("IT_S", "IT_C"));
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl college-a -am test -Dtest=ChoiceDaoIT`

- [ ] **Step 3：实现**

```java
// college-a/src/main/java/college/a/dao/ChoiceDao.java
package college.a.dao;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChoiceDao {
  public record Row(String courseId, String studentId, String score, String origin) {}

  private final DataSource ds;

  public ChoiceDao(DataSource ds) { this.ds = ds; }

  public int enrollLocal(String studentId, String courseId) {
    return insert(studentId, courseId, "A");
  }

  public int enrollFromOther(String studentId, String courseId, String origin) {
    return insert(studentId, courseId, origin);
  }

  private int insert(String studentId, String courseId, String origin) {
    String sql = "INSERT INTO 选课(课程编号,学生编号,成绩,来源) VALUES(?,?,NULL,?)";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, courseId); ps.setString(2, studentId); ps.setString(3, origin);
      return ps.executeUpdate();
    } catch (SQLException e) { throw new RuntimeException("enroll failed", e); }
  }

  public int withdraw(String studentId, String courseId) {
    String sql = "DELETE FROM 选课 WHERE 学生编号=? AND 课程编号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, studentId); ps.setString(2, courseId);
      return ps.executeUpdate();
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public boolean exists(String studentId, String courseId) {
    String sql = "SELECT 1 FROM 选课 WHERE 学生编号=? AND 课程编号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, studentId); ps.setString(2, courseId);
      try (var rs = ps.executeQuery()) { return rs.next(); }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findByStudent(String studentId) {
    String sql = "SELECT 课程编号,学生编号,成绩,来源 FROM 选课 WHERE 学生编号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, studentId);
      try (var rs = ps.executeQuery()) {
        List<Row> out = new ArrayList<>();
        while (rs.next()) out.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        return out;
      }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }
}
```

- [ ] **Step 4：PASS**

- [ ] **Step 5：commit**

```bash
git add college-a/src/main/java/college/a/dao/ChoiceDao.java college-a/src/test/java/college/a/dao/ChoiceDaoIT.java
git commit -m "feat(college-a): choice dao with enroll/withdraw/exists/findByStudent"
```

---

### Task 14：A 院课程行 ↔ A 格式 XML 适配器

**Files:**
- Create: `college-a/src/main/java/college/a/xml/CourseAAdapter.java`
- Test: `college-a/src/test/java/college/a/xml/CourseAAdapterTest.java`

> 适配器作用：把 DAO Row 序列化为符合 `classA.xsd` 的 XML，以及反序列化。后续 Plan 2 集成 Server 会基于此格式做 XSL 转换。

- [ ] **Step 1：写失败测试**

```java
// college-a/src/test/java/college/a/xml/CourseAAdapterTest.java
package college.a.xml;

import college.a.dao.CourseDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseAAdapterTest {
  @Test
  void marshal_then_validate_against_xsd() throws Exception {
    var rows = List.of(
        new CourseDao.Row("AC001", "数据库", 32, new BigDecimal("3.0"), "李老师", "A101", true),
        new CourseDao.Row("AC002", "编译", 48, new BigDecimal("4.0"), "王老师", "A102", false));
    String xml = CourseAAdapter.marshal(rows);
    var v = new XsdValidator(new StreamSource(getClass().getResourceAsStream("/schema/classA.xsd")));
    var r = v.validate(xml);
    assertTrue(r.valid(), r.errors().toString());
  }

  @Test
  void roundtrip_preserves_data() throws Exception {
    var original = List.of(
        new CourseDao.Row("AC001", "数据库", 32, new BigDecimal("3.0"), "李老师", "A101", true));
    String xml = CourseAAdapter.marshal(original);
    var parsed = CourseAAdapter.unmarshal(XmlIO.parseString(xml));
    assertEquals(1, parsed.size());
    assertEquals("AC001", parsed.get(0).id());
    assertEquals("数据库", parsed.get(0).name());
    assertTrue(parsed.get(0).shared());
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl college-a -am test -Dtest=CourseAAdapterTest`

- [ ] **Step 3：实现**

```java
// college-a/src/main/java/college/a/xml/CourseAAdapter.java
package college.a.xml;

import college.a.dao.CourseDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class CourseAAdapter {
  private CourseAAdapter() {}

  public static String marshal(List<CourseDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("课程集");
    for (var r : rows) {
      Element e = root.addElement("课程");
      e.addElement("课程编号").setText(r.id());
      e.addElement("课程名称").setText(r.name());
      e.addElement("课时").setText(Integer.toString(r.hours()));
      e.addElement("学分").setText(r.score().toPlainString());
      e.addElement("授课老师").setText(r.teacher());
      e.addElement("授课地点").setText(r.location());
      e.addElement("共享").setText(r.shared() ? "Y" : "N");
    }
    return XmlIO.toString(doc);
  }

  public static List<CourseDao.Row> unmarshal(Document doc) {
    List<CourseDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("课程")) {
      Element e = (Element) o;
      out.add(new CourseDao.Row(
          e.elementText("课程编号"),
          e.elementText("课程名称"),
          parseInt(e.elementText("课时")),
          new BigDecimal(e.elementText("学分")),
          e.elementText("授课老师"),
          e.elementText("授课地点"),
          "Y".equalsIgnoreCase(e.elementText("共享"))));
    }
    return out;
  }

  private static int parseInt(String s) { return s == null || s.isBlank() ? 0 : Integer.parseInt(s); }
}
```

- [ ] **Step 4：PASS**

- [ ] **Step 5：commit**

```bash
git add college-a/src/main/java/college/a/xml/CourseAAdapter.java college-a/src/test/java/college/a/xml/CourseAAdapterTest.java
git commit -m "feat(college-a): course xml adapter (marshal/unmarshal a-format)"
```

---

### Task 15：A 院学生行 ↔ A 格式 XML 适配器

**Files:**
- Create: `college-a/src/main/java/college/a/xml/StudentAAdapter.java`
- Test: `college-a/src/test/java/college/a/xml/StudentAAdapterTest.java`

- [ ] **Step 1：写失败测试**

```java
// college-a/src/test/java/college/a/xml/StudentAAdapterTest.java
package college.a.xml;

import college.a.dao.StudentDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import javax.xml.transform.stream.StreamSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StudentAAdapterTest {
  @Test
  void marshal_validates_against_xsd() {
    var rows = List.of(
        new StudentDao.Row("AS001", "张三", "男", "计算机", "as001"),
        new StudentDao.Row("AS002", "李四", "女", "计算机", null));
    String xml = StudentAAdapter.marshal(rows);
    var r = new XsdValidator(new StreamSource(getClass().getResourceAsStream("/schema/studentA.xsd"))).validate(xml);
    assertTrue(r.valid(), r.errors().toString());
  }

  @Test
  void roundtrip_preserves() throws Exception {
    var original = List.of(new StudentDao.Row("AS001", "张三", "男", "计算机", "as001"));
    var parsed = StudentAAdapter.unmarshal(XmlIO.parseString(StudentAAdapter.marshal(original)));
    assertEquals("张三", parsed.get(0).name());
    assertEquals("as001", parsed.get(0).accountRef());
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl college-a -am test -Dtest=StudentAAdapterTest`

- [ ] **Step 3：实现**

```java
// college-a/src/main/java/college/a/xml/StudentAAdapter.java
package college.a.xml;

import college.a.dao.StudentDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import java.util.ArrayList;
import java.util.List;

public final class StudentAAdapter {
  private StudentAAdapter() {}

  public static String marshal(List<StudentDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("学生集");
    for (var r : rows) {
      Element e = root.addElement("学生");
      e.addElement("学号").setText(r.id());
      e.addElement("姓名").setText(r.name());
      e.addElement("性别").setText(r.sex());
      e.addElement("院系").setText(r.dept());
      if (r.accountRef() != null) e.addElement("关联账户").setText(r.accountRef());
    }
    return XmlIO.toString(doc);
  }

  public static List<StudentDao.Row> unmarshal(Document doc) {
    List<StudentDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("学生")) {
      Element e = (Element) o;
      out.add(new StudentDao.Row(
          e.elementText("学号"), e.elementText("姓名"),
          e.elementText("性别"), e.elementText("院系"),
          e.elementText("关联账户")));
    }
    return out;
  }
}
```

- [ ] **Step 4：PASS**

- [ ] **Step 5：commit**

```bash
git add college-a/src/main/java/college/a/xml/StudentAAdapter.java college-a/src/test/java/college/a/xml/StudentAAdapterTest.java
git commit -m "feat(college-a): student xml adapter (a-format)"
```

---

### Task 16：A 院选课行 ↔ A 格式 XML 适配器

**Files:**
- Create: `college-a/src/main/java/college/a/xml/ChoiceAAdapter.java`
- Test: `college-a/src/test/java/college/a/xml/ChoiceAAdapterTest.java`

- [ ] **Step 1：写失败测试**

```java
// college-a/src/test/java/college/a/xml/ChoiceAAdapterTest.java
package college.a.xml;

import college.a.dao.ChoiceDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import javax.xml.transform.stream.StreamSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChoiceAAdapterTest {
  @Test
  void marshal_validates() {
    var rows = List.of(new ChoiceDao.Row("AC001", "AS001", "90", "A"),
                       new ChoiceDao.Row("AC002", "AS002", null, "A"));
    String xml = ChoiceAAdapter.marshal(rows);
    var r = new XsdValidator(new StreamSource(getClass().getResourceAsStream("/schema/choiceA.xsd"))).validate(xml);
    assertTrue(r.valid(), r.errors().toString());
  }

  @Test
  void roundtrip() throws Exception {
    var original = List.of(new ChoiceDao.Row("AC001", "AS001", "90", "A"));
    var parsed = ChoiceAAdapter.unmarshal(XmlIO.parseString(ChoiceAAdapter.marshal(original)));
    assertEquals("AS001", parsed.get(0).studentId());
    assertEquals("90", parsed.get(0).score());
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl college-a -am test -Dtest=ChoiceAAdapterTest`

- [ ] **Step 3：实现**

```java
// college-a/src/main/java/college/a/xml/ChoiceAAdapter.java
package college.a.xml;

import college.a.dao.ChoiceDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import java.util.ArrayList;
import java.util.List;

public final class ChoiceAAdapter {
  private ChoiceAAdapter() {}

  public static String marshal(List<ChoiceDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("选课集");
    for (var r : rows) {
      Element e = root.addElement("选课");
      e.addElement("课程编号").setText(r.courseId());
      e.addElement("学生编号").setText(r.studentId());
      if (r.score() != null) e.addElement("成绩").setText(r.score());
    }
    return XmlIO.toString(doc);
  }

  public static List<ChoiceDao.Row> unmarshal(Document doc) {
    List<ChoiceDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("选课")) {
      Element e = (Element) o;
      out.add(new ChoiceDao.Row(
          e.elementText("课程编号"),
          e.elementText("学生编号"),
          e.elementText("成绩"),
          "A"));
    }
    return out;
  }
}
```

- [ ] **Step 4：PASS**

- [ ] **Step 5：commit**

```bash
git add college-a/src/main/java/college/a/xml/ChoiceAAdapter.java college-a/src/test/java/college/a/xml/ChoiceAAdapterTest.java
git commit -m "feat(college-a): choice xml adapter (a-format)"
```

---

### Task 17：CollegeAServer 骨架 + 接受循环 + LOGIN 处理器

**Files:**
- Create: `college-a/src/main/java/college/a/server/CollegeAServer.java`
- Create: `college-a/src/main/java/college/a/server/CommandRouter.java`
- Create: `college-a/src/main/java/college/a/server/handler/LoginHandler.java`
- Test: `college-a/src/test/java/college/a/server/LoginHandlerTest.java`
- Test: `college-a/src/test/java/college/a/server/AcceptLoopTest.java`

> Server 骨架仅响应 LOGIN，确保协议帧 + 路由 + handler 三层串通。其他命令在 Task 18 接入。

- [ ] **Step 1：写 LoginHandler 单测（FAIL）**

```java
// college-a/src/test/java/college/a/server/LoginHandlerTest.java
package college.a.server;

import college.a.dao.AccountDao;
import college.a.server.handler.LoginHandler;
import college.a.service.AuthService;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginHandlerTest {
  @Test
  void login_ok() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(Optional.of(new AccountDao.Account("u","p","stu")));
    var h = new LoginHandler(new AuthService(dao));

    var req = new Message(Command.LOGIN, "r1",
        "<login><user>u</user><pass>p</pass></login>");
    var res = h.handle(req);

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<role>stu</role>"));
  }

  @Test
  void login_bad_password_returns_err() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(Optional.of(new AccountDao.Account("u","p","stu")));
    var h = new LoginHandler(new AuthService(dao));
    var res = h.handle(new Message(Command.LOGIN, "r2",
        "<login><user>u</user><pass>x</pass></login>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("AUTH_FAILED"));
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl college-a -am test -Dtest=LoginHandlerTest`

- [ ] **Step 3：实现 Handler 接口与 LoginHandler**

```java
// college-a/src/main/java/college/a/server/handler/Handler.java
package college.a.server.handler;

import cn.edu.di.protocol.Message;

public interface Handler {
  Message handle(Message request);
}
```

```java
// college-a/src/main/java/college/a/server/handler/LoginHandler.java
package college.a.server.handler;

import college.a.service.AuthService;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

public class LoginHandler implements Handler {
  private final AuthService auth;
  public LoginHandler(AuthService auth) { this.auth = auth; }

  @Override
  public Message handle(Message req) {
    try {
      var doc = XmlIO.parseString(req.payload());
      String user = doc.getRootElement().elementText("user");
      String pass = doc.getRootElement().elementText("pass");
      return auth.login(user, pass)
          .map(s -> Message.ok(req.requestId(),
              "<session><user>" + s.username() + "</user><role>" + s.role() + "</role></session>"))
          .orElseGet(() -> Message.err(req.requestId(), "AUTH_FAILED", "username or password incorrect"));
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
```

- [ ] **Step 4：实现 CommandRouter（注册式分发）**

```java
// college-a/src/main/java/college/a/server/CommandRouter.java
package college.a.server;

import college.a.server.handler.Handler;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import java.util.EnumMap;
import java.util.Map;

public class CommandRouter {
  private final Map<Command, Handler> handlers = new EnumMap<>(Command.class);

  public CommandRouter register(Command cmd, Handler h) {
    handlers.put(cmd, h);
    return this;
  }

  public Message dispatch(Message req) {
    Handler h = handlers.get(req.command());
    if (h == null) return Message.err(req.requestId(), "UNKNOWN_CMD", "no handler for " + req.command());
    return h.handle(req);
  }
}
```

- [ ] **Step 5：实现 CollegeAServer 接受循环**

```java
// college-a/src/main/java/college/a/server/CollegeAServer.java
package college.a.server;

import college.a.dao.AccountDao;
import college.a.jdbc.JdbcFactory;
import college.a.server.handler.LoginHandler;
import college.a.service.AuthService;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.protocol.MessageFrame;
import cn.edu.di.protocol.ProtocolException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class CollegeAServer implements AutoCloseable {
  private final ServerSocket server;
  private final CommandRouter router;
  private volatile boolean running = true;

  public CollegeAServer(int port, CommandRouter router) throws IOException {
    this.server = new ServerSocket(port);
    this.router = router;
  }

  public int port() { return server.getLocalPort(); }

  public void serve() {
    while (running) {
      try {
        Socket s = server.accept();
        Thread.startVirtualThread(() -> handleClient(s));
      } catch (IOException e) {
        if (running) System.err.println("accept failed: " + e.getMessage());
      }
    }
  }

  private void handleClient(Socket s) {
    try (s) {
      Message req = Message.read(s.getInputStream());
      Message res = router.dispatch(req);
      Message.write(s.getOutputStream(), res);
    } catch (ProtocolException pe) {
      try { Message.write(s.getOutputStream(),
          Message.err("0", "PROTOCOL", pe.getMessage())); } catch (IOException ignore) {}
    } catch (IOException ignore) {}
  }

  @Override public void close() throws IOException {
    running = false;
    server.close();
  }

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getProperty("port", "9001"));
    var ds = JdbcFactory.fromClasspath("/db.properties");
    var router = new CommandRouter()
        .register(Command.LOGIN, new LoginHandler(new AuthService(new AccountDao(ds))));
    try (var srv = new CollegeAServer(port, router)) {
      System.out.println("College A server listening on " + srv.port());
      srv.serve();
    }
  }
}
```

- [ ] **Step 6：写接受循环端到端测试（FAIL）**

```java
// college-a/src/test/java/college/a/server/AcceptLoopTest.java
package college.a.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.protocol.MessageFrame;
import org.junit.jupiter.api.Test;
import java.net.Socket;
import static org.junit.jupiter.api.Assertions.*;

class AcceptLoopTest {
  @Test
  void unknown_command_returns_err() throws Exception {
    var router = new CommandRouter();
    try (var srv = new CollegeAServer(0, router)) {
      Thread t = Thread.startVirtualThread(srv::serve);
      try (var sock = new Socket("127.0.0.1", srv.port())) {
        Message.write(sock.getOutputStream(),
            new Message(Command.LOGIN, "rx", "<x/>"));
        Message res = Message.read(sock.getInputStream());
        assertEquals(Command.ERR, res.command());
        assertTrue(res.payload().contains("UNKNOWN_CMD"));
      }
    }
  }
}
```

- [ ] **Step 7：PASS**

Run: `mvn -pl college-a -am test -Dtest=LoginHandlerTest,AcceptLoopTest`

- [ ] **Step 8：commit**

```bash
git add college-a/src/main/java/college/a/server/ college-a/src/test/java/college/a/server/
git commit -m "feat(college-a): server skeleton with router and login handler"
```

---

### Task 18：LIST_LOCAL_COURSES + 本院 ENROLL + 本院 WITHDRAW 处理器

**Files:**
- Create: `college-a/src/main/java/college/a/server/handler/ListLocalCoursesHandler.java`
- Create: `college-a/src/main/java/college/a/server/handler/EnrollLocalHandler.java`
- Create: `college-a/src/main/java/college/a/server/handler/WithdrawLocalHandler.java`
- Modify: `college-a/src/main/java/college/a/server/CollegeAServer.java`（在 main 注册新 handler）
- Test: `college-a/src/test/java/college/a/server/handler/CourseHandlersTest.java`

> 跨院判断与 ENROLL/WITHDRAW 的远端分支放到 Plan 2。这里只覆盖"已知是本院课程"的路径。

- [ ] **Step 1：写失败测试（mock DAO，纯单测）**

```java
// college-a/src/test/java/college/a/server/handler/CourseHandlersTest.java
package college.a.server.handler;

import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseHandlersTest {
  @Test
  void list_local_returns_a_format_xml() {
    var dao = mock(CourseDao.class);
    when(dao.findAll()).thenReturn(List.of(
        new CourseDao.Row("AC001", "数据库", 32, new BigDecimal("3.0"), "李", "A101", true)));
    var res = new ListLocalCoursesHandler(dao).handle(new Message(Command.LIST_LOCAL_COURSES, "r1", ""));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<课程编号>AC001</课程编号>"));
  }

  @Test
  void enroll_local_inserts_when_course_present() {
    var courseDao = mock(CourseDao.class);
    var choiceDao = mock(ChoiceDao.class);
    when(courseDao.findById("AC001")).thenReturn(Optional.of(
        new CourseDao.Row("AC001", "数据库", 32, new BigDecimal("3.0"), "李", "A101", false)));
    when(choiceDao.exists("AS001", "AC001")).thenReturn(false);
    when(choiceDao.enrollLocal("AS001", "AC001")).thenReturn(1);

    var res = new EnrollLocalHandler(courseDao, choiceDao).handle(
        new Message(Command.ENROLL, "r1",
            "<选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号></选课>"));
    assertEquals(Command.OK, res.command());
    verify(choiceDao).enrollLocal("AS001", "AC001");
  }

  @Test
  void enroll_returns_err_when_already_enrolled() {
    var courseDao = mock(CourseDao.class);
    var choiceDao = mock(ChoiceDao.class);
    when(courseDao.findById("AC001")).thenReturn(Optional.of(
        new CourseDao.Row("AC001", "数据库", 32, new BigDecimal("3.0"), "李", "A101", false)));
    when(choiceDao.exists("AS001", "AC001")).thenReturn(true);

    var res = new EnrollLocalHandler(courseDao, choiceDao).handle(
        new Message(Command.ENROLL, "r1",
            "<选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号></选课>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("ALREADY_ENROLLED"));
  }

  @Test
  void withdraw_returns_err_when_no_record() {
    var dao = mock(ChoiceDao.class);
    when(dao.withdraw("AS001", "AC001")).thenReturn(0);
    var res = new WithdrawLocalHandler(dao).handle(
        new Message(Command.WITHDRAW, "r1",
            "<选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号></选课>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("NO_SUCH_CHOICE"));
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl college-a -am test -Dtest=CourseHandlersTest`

- [ ] **Step 3：实现 ListLocalCoursesHandler**

```java
// college-a/src/main/java/college/a/server/handler/ListLocalCoursesHandler.java
package college.a.server.handler;

import college.a.dao.CourseDao;
import college.a.xml.CourseAAdapter;
import cn.edu.di.protocol.Message;

public class ListLocalCoursesHandler implements Handler {
  private final CourseDao dao;
  public ListLocalCoursesHandler(CourseDao dao) { this.dao = dao; }

  @Override
  public Message handle(Message req) {
    return Message.ok(req.requestId(), CourseAAdapter.marshal(dao.findAll()));
  }
}
```

- [ ] **Step 4：实现 EnrollLocalHandler**

```java
// college-a/src/main/java/college/a/server/handler/EnrollLocalHandler.java
package college.a.server.handler;

import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

public class EnrollLocalHandler implements Handler {
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;

  public EnrollLocalHandler(CourseDao courseDao, ChoiceDao choiceDao) {
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      var doc = XmlIO.parseString(req.payload());
      String cid = doc.getRootElement().elementText("课程编号");
      String sid = doc.getRootElement().elementText("学生编号");

      var course = courseDao.findById(cid);
      if (course.isEmpty()) return Message.err(req.requestId(), "NO_SUCH_COURSE", cid);
      if (choiceDao.exists(sid, cid)) return Message.err(req.requestId(), "ALREADY_ENROLLED", sid + "/" + cid);

      int n = choiceDao.enrollLocal(sid, cid);
      return n == 1 ? Message.ok(req.requestId(), "<result>enrolled</result>")
                    : Message.err(req.requestId(), "DB_FAILURE", "rows=" + n);
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
```

- [ ] **Step 5：实现 WithdrawLocalHandler**

```java
// college-a/src/main/java/college/a/server/handler/WithdrawLocalHandler.java
package college.a.server.handler;

import college.a.dao.ChoiceDao;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

public class WithdrawLocalHandler implements Handler {
  private final ChoiceDao dao;
  public WithdrawLocalHandler(ChoiceDao dao) { this.dao = dao; }

  @Override
  public Message handle(Message req) {
    try {
      var doc = XmlIO.parseString(req.payload());
      String cid = doc.getRootElement().elementText("课程编号");
      String sid = doc.getRootElement().elementText("学生编号");
      int n = dao.withdraw(sid, cid);
      return n == 1 ? Message.ok(req.requestId(), "<result>withdrawn</result>")
                    : Message.err(req.requestId(), "NO_SUCH_CHOICE", sid + "/" + cid);
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
```

- [ ] **Step 6：在 main 中注册**

修改 `CollegeAServer.main`：

```java
var courseDao = new college.a.dao.CourseDao(ds);
var choiceDao = new college.a.dao.ChoiceDao(ds);
var router = new CommandRouter()
    .register(Command.LOGIN, new LoginHandler(new AuthService(new AccountDao(ds))))
    .register(Command.LIST_LOCAL_COURSES,
        new college.a.server.handler.ListLocalCoursesHandler(courseDao))
    .register(Command.ENROLL,
        new college.a.server.handler.EnrollLocalHandler(courseDao, choiceDao))
    .register(Command.WITHDRAW,
        new college.a.server.handler.WithdrawLocalHandler(choiceDao));
```

- [ ] **Step 7：PASS**

Run: `mvn -pl college-a -am test -Dtest=CourseHandlersTest`

- [ ] **Step 8：commit**

```bash
git add college-a/src/main/java/college/a/server/handler/ college-a/src/main/java/college/a/server/CollegeAServer.java college-a/src/test/java/college/a/server/handler/
git commit -m "feat(college-a): list/enroll/withdraw local handlers"
```

---

### Task 19：IntegrationServer 骨架 + 命令路由 + PING 处理器

**Files:**
- Create: `integration/src/main/java/integration/server/IntegrationServer.java`
- Create: `integration/src/main/java/integration/server/IntegrationRouter.java`
- Create: `integration/src/main/java/integration/server/handler/PingHandler.java`
- Test: `integration/src/test/java/integration/server/PingHandlerTest.java`
- Test: `integration/src/test/java/integration/server/AcceptLoopTest.java`

> Plan 1 阶段集成 Server 仅响应 PING（健康检查）。所有跨院命令路由器留到 Plan 2 注册。

- [ ] **Step 1：先在 cn.edu.di.protocol.Command 加入 PING**

修改 `common/src/main/java/common/protocol/Command.java`，将枚举里加 `PING`（如尚未存在）。同时确保 OK / ERR / LOGIN 等已有。

- [ ] **Step 2：写 PingHandler 测试**

```java
// integration/src/test/java/integration/server/PingHandlerTest.java
package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.server.handler.PingHandler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PingHandlerTest {
  @Test
  void ping_returns_ok_with_pong() {
    var res = new PingHandler().handle(new Message(Command.PING, "r1", ""));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("pong"));
  }
}
```

- [ ] **Step 3：FAIL**

Run: `mvn -pl integration -am test -Dtest=PingHandlerTest`

- [ ] **Step 4：实现 PingHandler 与 Handler 接口**

```java
// integration/src/main/java/integration/server/handler/Handler.java
package integration.server.handler;

import cn.edu.di.protocol.Message;

public interface Handler {
  Message handle(Message request);
}
```

```java
// integration/src/main/java/integration/server/handler/PingHandler.java
package integration.server.handler;

import cn.edu.di.protocol.Message;

public class PingHandler implements Handler {
  @Override
  public Message handle(Message req) {
    return Message.ok(req.requestId(), "<pong>integration-server</pong>");
  }
}
```

- [ ] **Step 5：实现 IntegrationRouter（与 college 那侧结构一致，独立类好读）**

```java
// integration/src/main/java/integration/server/IntegrationRouter.java
package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.server.handler.Handler;
import java.util.EnumMap;
import java.util.Map;

public class IntegrationRouter {
  private final Map<Command, Handler> handlers = new EnumMap<>(Command.class);

  public IntegrationRouter register(Command cmd, Handler h) {
    handlers.put(cmd, h); return this;
  }

  public Message dispatch(Message req) {
    Handler h = handlers.get(req.command());
    if (h == null) return Message.err(req.requestId(), "UNKNOWN_CMD", "no handler for " + req.command());
    return h.handle(req);
  }
}
```

- [ ] **Step 6：实现 IntegrationServer**

```java
// integration/src/main/java/integration/server/IntegrationServer.java
package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.protocol.MessageFrame;
import cn.edu.di.protocol.ProtocolException;
import integration.server.handler.PingHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class IntegrationServer implements AutoCloseable {
  private final ServerSocket server;
  private final IntegrationRouter router;
  private volatile boolean running = true;

  public IntegrationServer(int port, IntegrationRouter router) throws IOException {
    this.server = new ServerSocket(port);
    this.router = router;
  }

  public int port() { return server.getLocalPort(); }

  public void serve() {
    while (running) {
      try {
        Socket s = server.accept();
        Thread.startVirtualThread(() -> handle(s));
      } catch (IOException e) {
        if (running) System.err.println("accept failed: " + e.getMessage());
      }
    }
  }

  private void handle(Socket s) {
    try (s) {
      Message req = Message.read(s.getInputStream());
      Message res = router.dispatch(req);
      Message.write(s.getOutputStream(), res);
    } catch (ProtocolException pe) {
      try { Message.write(s.getOutputStream(),
          Message.err("0", "PROTOCOL", pe.getMessage())); } catch (IOException ignore) {}
    } catch (IOException ignore) {}
  }

  @Override public void close() throws IOException { running = false; server.close(); }

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getProperty("port", "9100"));
    var router = new IntegrationRouter().register(Command.PING, new PingHandler());
    try (var srv = new IntegrationServer(port, router)) {
      System.out.println("Integration server listening on " + srv.port());
      srv.serve();
    }
  }
}
```

- [ ] **Step 7：写接受循环端到端测试**

```java
// integration/src/test/java/integration/server/AcceptLoopTest.java
package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.protocol.MessageFrame;
import integration.server.handler.PingHandler;
import org.junit.jupiter.api.Test;
import java.net.Socket;
import static org.junit.jupiter.api.Assertions.*;

class AcceptLoopTest {
  @Test
  void ping_roundtrip() throws Exception {
    var router = new IntegrationRouter().register(Command.PING, new PingHandler());
    try (var srv = new IntegrationServer(0, router)) {
      Thread.startVirtualThread(srv::serve);
      try (var sock = new Socket("127.0.0.1", srv.port())) {
        Message.write(sock.getOutputStream(),
            new Message(Command.PING, "p1", ""));
        Message res = Message.read(sock.getInputStream());
        assertEquals(Command.OK, res.command());
        assertTrue(res.payload().contains("pong"));
      }
    }
  }
}
```

- [ ] **Step 8：PASS**

Run: `mvn -pl integration -am test`

- [ ] **Step 9：commit**

```bash
git add common/src/main/java/common/protocol/Command.java integration/src/main/java/ integration/src/test/java/
git commit -m "feat(integration): server skeleton with router and ping handler"
```

---

### Task 20：Swing 客户端（登录窗口 + 课程列表）

**Files:**
- Create: `client/src/main/java/client/Main.java`
- Create: `client/src/main/java/client/net/CollegeClient.java`
- Create: `client/src/main/java/client/ui/LoginFrame.java`
- Create: `client/src/main/java/client/ui/CourseListFrame.java`
- Create: `client/src/main/resources/client.properties`
- Test: `client/src/test/java/client/net/CollegeClientTest.java`

> 客户端通过 `--college=A` + `--server=host:port` 启动；本任务只做"登录 + 看本院课程列表"两个界面。GUI 不写自动化测试（手动冒烟），但网络层可以测。

- [ ] **Step 1：写 CollegeClient 的网络往返测试（FAIL）**

```java
// client/src/test/java/client/net/CollegeClientTest.java
package client.net;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.protocol.MessageFrame;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import static org.junit.jupiter.api.Assertions.*;

class CollegeClientTest {
  @Test
  void send_returns_server_response() throws Exception {
    try (var server = new ServerSocket(0)) {
      Thread.startVirtualThread(() -> {
        try (var s = server.accept()) {
          Message req = Message.read(s.getInputStream());
          Message.write(s.getOutputStream(),
              Message.ok(req.requestId(), "<echo>" + req.payload() + "</echo>"));
        } catch (Exception ignore) {}
      });
      var client = new CollegeClient("127.0.0.1", server.getLocalPort());
      Message res = client.send(new Message(Command.LOGIN, "r1", "<x/>"));
      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<echo><x/></echo>"));
    }
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl client -am test -Dtest=CollegeClientTest`

- [ ] **Step 3：实现 CollegeClient**

```java
// client/src/main/java/client/net/CollegeClient.java
package client.net;

import cn.edu.di.protocol.Message;
import cn.edu.di.protocol.MessageFrame;
import java.io.IOException;
import java.net.Socket;

public class CollegeClient {
  private final String host;
  private final int port;

  public CollegeClient(String host, int port) {
    this.host = host; this.port = port;
  }

  public Message send(Message req) throws IOException {
    try (var sock = new Socket(host, port)) {
      Message.write(sock.getOutputStream(), req);
      return Message.read(sock.getInputStream());
    }
  }
}
```

- [ ] **Step 4：PASS**

Run: `mvn -pl client -am test -Dtest=CollegeClientTest`

- [ ] **Step 5：实现 LoginFrame**

```java
// client/src/main/java/client/ui/LoginFrame.java
package client.ui;

import client.net.CollegeClient;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.UUID;

public class LoginFrame extends JFrame {
  private final CollegeClient client;
  private final String college;
  private final JTextField user = new JTextField(15);
  private final JPasswordField pass = new JPasswordField(15);
  private final JLabel status = new JLabel(" ");

  public LoginFrame(String college, CollegeClient client) {
    super("学院 " + college + " 教务系统 - 登录");
    this.client = client;
    this.college = college;

    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setLayout(new GridBagLayout());
    var c = new GridBagConstraints();
    c.insets = new Insets(6, 6, 6, 6);

    c.gridx=0; c.gridy=0; add(new JLabel("账号:"), c);
    c.gridx=1; add(user, c);
    c.gridx=0; c.gridy=1; add(new JLabel("密码:"), c);
    c.gridx=1; add(pass, c);

    var btn = new JButton("登录");
    btn.addActionListener(this::onLogin);
    c.gridx=0; c.gridy=2; c.gridwidth=2; add(btn, c);
    c.gridy=3; add(status, c);

    pack();
    setLocationRelativeTo(null);
  }

  private void onLogin(ActionEvent ev) {
    String u = user.getText().trim();
    String p = new String(pass.getPassword());
    String xml = "<login><user>" + u + "</user><pass>" + p + "</pass></login>";
    try {
      Message res = client.send(new Message(Command.LOGIN, UUID.randomUUID().toString(), xml));
      if (res.command() == Command.OK) {
        var doc = XmlIO.parseString(res.payload());
        String role = doc.getRootElement().elementText("role");
        dispose();
        new CourseListFrame(college, u, role, client).setVisible(true);
      } else {
        status.setText("登录失败：" + res.payload());
      }
    } catch (Exception e) {
      status.setText("网络错误：" + e.getMessage());
    }
  }
}
```

- [ ] **Step 6：实现 CourseListFrame**

```java
// client/src/main/java/client/ui/CourseListFrame.java
package client.ui;

import client.net.CollegeClient;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Element;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

public class CourseListFrame extends JFrame {
  private final CollegeClient client;
  private final DefaultTableModel model = new DefaultTableModel(
      new String[]{"课程编号", "课程名称", "学分", "授课老师", "授课地点", "共享"}, 0);

  public CourseListFrame(String college, String username, String role, CollegeClient client) {
    super("学院 " + college + "  欢迎 " + username + "（" + role + "）");
    this.client = client;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(720, 480);
    setLayout(new BorderLayout());

    add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);

    var refresh = new JButton("刷新本院课程");
    refresh.addActionListener(e -> reload());
    add(refresh, BorderLayout.SOUTH);

    setLocationRelativeTo(null);
    reload();
  }

  private void reload() {
    try {
      Message res = client.send(new Message(Command.LIST_LOCAL_COURSES, UUID.randomUUID().toString(), ""));
      if (res.command() != Command.OK) {
        JOptionPane.showMessageDialog(this, res.payload(), "查询失败", JOptionPane.ERROR_MESSAGE);
        return;
      }
      model.setRowCount(0);
      var doc = XmlIO.parseString(res.payload());
      for (Object o : doc.getRootElement().elements("课程")) {
        Element c = (Element) o;
        model.addRow(new Object[]{
            c.elementText("课程编号"), c.elementText("课程名称"),
            c.elementText("学分"), c.elementText("授课老师"),
            c.elementText("授课地点"), c.elementText("共享")
        });
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
    }
  }
}
```

- [ ] **Step 7：实现 Main**

```java
// client/src/main/java/client/Main.java
package client;

import client.net.CollegeClient;
import client.ui.LoginFrame;
import javax.swing.SwingUtilities;
import java.util.HashMap;
import java.util.Map;

public class Main {
  public static void main(String[] args) {
    Map<String, String> opt = parse(args);
    String college = opt.getOrDefault("--college", "A");
    String[] hp = opt.getOrDefault("--server", "127.0.0.1:9001").split(":");
    var client = new CollegeClient(hp[0], Integer.parseInt(hp[1]));
    SwingUtilities.invokeLater(() -> new LoginFrame(college, client).setVisible(true));
  }

  private static Map<String, String> parse(String[] args) {
    var m = new HashMap<String, String>();
    for (String a : args) {
      int eq = a.indexOf('=');
      if (a.startsWith("--") && eq > 0) m.put(a.substring(0, eq), a.substring(eq + 1));
    }
    return m;
  }
}
```

- [ ] **Step 8：commit**

```bash
git add client/
git commit -m "feat(client): swing login + local course list"
```

---

### Task 21：Seed 数据生成器（A 院 50 学生 / 10 课程 / 5 选课）

**Files:**
- Create: `scripts/seed-data/src/main/java/seed/SeedA.java`
- Create: `scripts/seed-data/pom.xml`
- Modify: `pom.xml`（加入 scripts/seed-data 模块）
- Test: `scripts/seed-data/src/test/java/seed/SeedAGeneratorTest.java`

> 生成器输出独立的 `init_a_data.sql`（仅 INSERT，schema 在 Task 8 已建好），用脚本灌入。

- [ ] **Step 1：写测试（FAIL）**

```java
// scripts/seed-data/src/test/java/seed/SeedAGeneratorTest.java
package seed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedAGeneratorTest {
  @Test
  void generates_50_students_10_courses_250_choices() {
    var data = SeedA.generate(42L);  // fixed seed
    assertEquals(50, data.students().size());
    assertEquals(10, data.courses().size());
    assertEquals(50 * 5, data.choices().size());
    assertTrue(data.courses().stream().filter(c -> c.shared()).count() >= 3);
  }

  @Test
  void student_ids_unique_and_prefixed() {
    var data = SeedA.generate(1L);
    assertEquals(50, data.students().stream().map(s -> s.id()).distinct().count());
    assertTrue(data.students().stream().allMatch(s -> s.id().startsWith("AS")));
  }

  @Test
  void to_sql_contains_inserts() {
    String sql = SeedA.toSql(SeedA.generate(1L));
    assertTrue(sql.contains("INSERT INTO 账户"));
    assertTrue(sql.contains("INSERT INTO 学生"));
    assertTrue(sql.contains("INSERT INTO 课程"));
    assertTrue(sql.contains("INSERT INTO 选课"));
  }
}
```

- [ ] **Step 2：FAIL**

Run: `mvn -pl scripts/seed-data -am test -Dtest=SeedAGeneratorTest`

- [ ] **Step 3：实现**

```java
// scripts/seed-data/src/main/java/seed/SeedA.java
package seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SeedA {
  private SeedA() {}

  public record Student(String id, String name, String sex, String dept, String account) {}
  public record Course(String id, String name, int hours, double score, String teacher, String location, boolean shared) {}
  public record Choice(String studentId, String courseId) {}
  public record Data(List<Student> students, List<Course> courses, List<Choice> choices) {}

  private static final String[] SURNAMES = {"张","王","李","赵","陈","刘","杨","黄","周","吴"};
  private static final String[] GIVEN = {"伟","芳","娜","敏","静","秀英","丽","强","磊","军","洋","勇","艳","杰","涛","明","超","秀兰","霞","平"};

  public static Data generate(long seed) {
    Random r = new Random(seed);
    List<Student> students = new ArrayList<>();
    for (int i = 1; i <= 50; i++) {
      String id = String.format("AS%03d", i);
      String name = SURNAMES[r.nextInt(SURNAMES.length)] + GIVEN[r.nextInt(GIVEN.length)];
      String sex = r.nextBoolean() ? "男" : "女";
      students.add(new Student(id, name, sex, "计算机", id.toLowerCase()));
    }

    String[] cnames = {"数据库原理","编译原理","操作系统","算法分析","计算机网络",
                       "软件工程","人工智能","机器学习","离散数学","数据结构"};
    List<Course> courses = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      String id = String.format("AC%03d", i + 1);
      boolean shared = i < 4; // 4 共享
      courses.add(new Course(id, cnames[i], 32 + r.nextInt(17),
          2.0 + r.nextInt(3), "李老师" + (i % 3), "A" + (101 + i), shared));
    }

    List<Choice> choices = new ArrayList<>();
    for (Student s : students) {
      List<Course> shuffled = new ArrayList<>(courses);
      Collections.shuffle(shuffled, r);
      for (int k = 0; k < 5; k++) {
        choices.add(new Choice(s.id(), shuffled.get(k).id()));
      }
    }
    return new Data(students, courses, choices);
  }

  public static String toSql(Data d) {
    StringBuilder sb = new StringBuilder();
    sb.append("DELETE FROM 选课;\nDELETE FROM 学生;\nDELETE FROM 课程;\nDELETE FROM 账户;\nGO\n");
    for (Student s : d.students()) {
      sb.append(String.format("INSERT INTO 账户(账户名,密码,权限) VALUES('%s','%s','stu');%n",
          s.account(), "pwd" + s.id().substring(2)));
    }
    sb.append("INSERT INTO 账户(账户名,密码,权限) VALUES('admin','admin1','adm');\nGO\n");
    for (Student s : d.students()) {
      sb.append(String.format("INSERT INTO 学生(学号,姓名,性别,院系,关联账户) VALUES('%s','%s','%s','%s','%s');%n",
          s.id(), s.name(), s.sex(), s.dept(), s.account()));
    }
    for (Course c : d.courses()) {
      sb.append(String.format("INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) " +
              "VALUES('%s','%s',%d,%.1f,'%s','%s','%s');%n",
          c.id(), c.name(), c.hours(), c.score(), c.teacher(), c.location(), c.shared() ? "Y" : "N"));
    }
    sb.append("GO\n");
    for (Choice ch : d.choices()) {
      sb.append(String.format("INSERT INTO 选课(课程编号,学生编号,来源) VALUES('%s','%s','A');%n",
          ch.courseId(), ch.studentId()));
    }
    sb.append("GO\n");
    return sb.toString();
  }

  public static void main(String[] args) throws IOException {
    long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
    Path out = Path.of("college-a/src/main/resources/sql/init_a_data.sql");
    Files.createDirectories(out.getParent());
    Files.writeString(out, toSql(generate(seed)));
    System.out.println("wrote " + out);
  }
}
```

POM 加入子模块（在父 `pom.xml` `<modules>` 中追加 `<module>scripts/seed-data</module>`）；`scripts/seed-data/pom.xml` 与其他模块同样基础结构（artifactId=`seed-data`，依赖 JUnit 5）。

- [ ] **Step 4：PASS**

Run: `mvn -pl scripts/seed-data -am test`

- [ ] **Step 5：生成并灌入**

```bash
mvn -pl scripts/seed-data -am exec:java -Dexec.mainClass=seed.SeedA -Dexec.args="42"
docker exec -i di-sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U SA -P 'Di_Strong_Pwd!2024' -No -d di_a \
  < college-a/src/main/resources/sql/init_a_data.sql
```

预期：无错误；查询 `SELECT COUNT(*) FROM 学生`，应返回 50。

- [ ] **Step 6：commit**

```bash
git add scripts/seed-data/ pom.xml college-a/src/main/resources/sql/init_a_data.sql
git commit -m "feat(seed): generator for college a (50/10/250) and seeded data sql"
```

---

### Task 22：start-all.sh + 手工冒烟测试

**Files:**
- Create: `scripts/start-all.sh`
- Create: `scripts/stop-all.sh`
- Create: `docs/smoke-plan1.md`

> Plan 1 阶段只启动 College A Server + Integration Server + 一个 A 客户端（B/C 没实现）。给冒烟脚本与流程清单，作为 Plan 1 完成的人工验收点。

- [ ] **Step 1：写 start-all.sh**

```bash
# scripts/start-all.sh
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p logs
echo "[1/3] 启动 SQL Server (Docker) ..."
./scripts/db/start-sqlserver.sh

echo "[2/3] 构建 jar (skip tests) ..."
mvn -q -DskipTests package

echo "[3/3] 启动 Integration Server 与 College A Server ..."
java -Dport=9100 -cp integration/target/classes:common/target/classes:$(cat integration/target/classpath.txt 2>/dev/null || echo '') \
    integration.server.IntegrationServer >logs/integration.log 2>&1 &
echo $! > logs/integration.pid

java -Dport=9001 -cp college-a/target/classes:common/target/classes:$(cat college-a/target/classpath.txt 2>/dev/null || echo '') \
    college.a.server.CollegeAServer >logs/college-a.log 2>&1 &
echo $! > logs/college-a.pid

echo "服务器已启动。日志在 logs/。客户端启动："
echo "  java -cp client/target/classes:common/target/classes client.Main --college=A --server=127.0.0.1:9001"
```

> 注：classpath 通过 `mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt` 提前生成（在 `package` 流程里加上即可，留作执行计划时落地）。

```bash
# scripts/stop-all.sh
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for pidf in logs/integration.pid logs/college-a.pid; do
  [ -f "$pidf" ] && kill "$(cat "$pidf")" 2>/dev/null || true
  rm -f "$pidf"
done
docker stop di-sqlserver 2>/dev/null || true
echo "已停止"
```

赋权：

```bash
chmod +x scripts/start-all.sh scripts/stop-all.sh
```

- [ ] **Step 2：写冒烟清单**

```markdown
# Plan 1 冒烟测试清单（手工）

## 前置
- Docker 运行中
- 已执行 `mvn -DskipTests package` 与 `seed.SeedA`

## 步骤
1. `./scripts/start-all.sh`，确认 logs/ 下两个日志各有"listening on"行
2. 启动客户端：`java -cp ... client.Main --college=A --server=127.0.0.1:9001`
3. 用账号 `as001` / 密码 `pwd001` 登录 → 跳到课程列表窗口
4. 点击"刷新本院课程"，应看到 10 行
5. 用错密码 `xxx` 登录 → 弹出"AUTH_FAILED"
6. 通过 `nc 127.0.0.1 9100` 发 PING 命令（手工拼帧），收到 `<pong>integration-server</pong>`
7. 关闭窗口；`./scripts/stop-all.sh`
```

- [ ] **Step 3：手工跑一遍冒烟**

按上面 7 步操作；观察到的偏差记录到 `docs/smoke-plan1.md`，并修回相应任务。

- [ ] **Step 4：commit**

```bash
git add scripts/start-all.sh scripts/stop-all.sh docs/smoke-plan1.md
git commit -m "build: start/stop scripts and plan 1 smoke checklist"
```

---

## Plan 1 验收

完成本计划后应满足：

- 项目能 `mvn package` 成功，所有单元测试与 IT 全过
- College A Server 能用客户端 GUI 登录
- 客户端可看到本院 10 门课程；可成功本院选课/退课（通过 GUI 之外的客户端调用，或 Plan 3 时再补 GUI 入口）
- Integration Server PING 健康检查通畅
- 协议层、XML 层、XSD 层、数据库层都有可重跑测试
- 项目结构如设计 §3 所示，B/C 模块为空骨架（Plan 2 落实）

**下一步：进入 Plan 2，继续实现 College B（Oracle）、College C（MySQL）的对应内容，以及 XSL 转换层与跨院流程。**
