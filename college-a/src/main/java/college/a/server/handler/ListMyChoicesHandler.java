package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsltTransformer;
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
        if (pulledClasses != null && !pulledClasses.elements().isEmpty()) {
          String localized = XsltTransformer.fromClasspath("/xsl/unifiedMyChoiceToA.xsl")
              .transform(pulledClasses.asXML());
          Element localRoot = XmlIO.parse(localized).getRootElement();
          for (Object o : localRoot.elements()) {
            crossEnrolled.add(((Element) o).createCopy());
          }
        }
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
