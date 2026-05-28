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
