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
