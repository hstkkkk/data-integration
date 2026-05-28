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
