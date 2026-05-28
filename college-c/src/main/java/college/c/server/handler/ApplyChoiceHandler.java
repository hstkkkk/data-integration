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
