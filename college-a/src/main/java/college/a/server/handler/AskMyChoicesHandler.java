package college.a.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.StringWriter;

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
      return Message.ok(req.requestId(), writeXml(doc));
    } catch (RuntimeException e) {
      return Message.err(req.requestId(), "LOCAL_QUERY_FAILED", e.getMessage());
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }

  private static String writeXml(Document doc) throws Exception {
    StringWriter sw = new StringWriter();
    OutputFormat fmt = OutputFormat.createPrettyPrint();
    fmt.setEncoding("UTF-8");
    fmt.setExpandEmptyElements(true);
    XMLWriter xw = new XMLWriter(sw, fmt);
    xw.write(doc);
    xw.flush();
    return sw.toString();
  }
}
