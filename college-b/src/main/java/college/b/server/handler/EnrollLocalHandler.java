package college.b.server.handler;

import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
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
      String courseId = doc.getRootElement().elementText("课程编号");
      String studentId = doc.getRootElement().elementText("学号");

      var course = courseDao.findById(courseId);
      if (course.isEmpty()) return Message.err(request.requestId(), "NO_SUCH_COURSE", courseId);
      if (choiceDao.exists(studentId, courseId))
        return Message.err(request.requestId(), "ALREADY_ENROLLED", studentId + "/" + courseId);

      choiceDao.enroll(studentId, courseId);
      return Message.ok(request.requestId(), "");
    } catch (Exception e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
