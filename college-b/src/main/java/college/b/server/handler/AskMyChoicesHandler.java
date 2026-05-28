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
