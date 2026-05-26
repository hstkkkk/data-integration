package college.c.server.handler;

import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
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
  public Message handle(Message request) {
    try {
      var doc = XmlIO.parse(request.payload());
      String courseId = doc.getRootElement().elementText("Cno");
      String studentId = doc.getRootElement().elementText("Sno");

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
}
