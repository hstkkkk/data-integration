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

  private final CollegeClient clientA;
  private final CollegeClient clientB;
  private final CollegeClient clientC;

  public FetchSharedCoursesHandler(CollegeClient clientA, CollegeClient clientB, CollegeClient clientC) {
    this.clientA = clientA;
    this.clientB = clientB;
    this.clientC = clientC;
  }

  @Override
  public Message handle(Message req) {
    try {
      String fromCollege = parseFromCollege(req.payload());
      Document merged = DocumentHelper.createDocument();
      Element root = merged.addElement("classes");

      if (!"A".equals(fromCollege)) {
        collectFromCollege(root, clientA, "A",
            XsltTransformer.fromClasspath("/xsl/formatA.xsl"),
            XsdValidator.fromClasspath("/schema/formatClass.xsd"));
      }
      if (!"B".equals(fromCollege)) {
        collectFromCollege(root, clientB, "B",
            XsltTransformer.fromClasspath("/xsl/formatB.xsl"),
            XsdValidator.fromClasspath("/schema/formatClass.xsd"));
      }
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
    try {
      var root = XmlIO.parse(xml).getRootElement();
      String text = "from".equals(root.getName()) ? root.getText() : root.elementText("from");
      return text == null ? "" : text.trim();
    } catch (Exception e) { return ""; }
  }
}
