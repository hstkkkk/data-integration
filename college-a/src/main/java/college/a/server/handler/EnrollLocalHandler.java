package college.a.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlException;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import org.dom4j.Document;
import org.dom4j.Element;

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
      Document doc = XmlIO.parse(request.payload());
      Element root = doc.getRootElement();
      String courseId = root.elementText("课程编号");
      String studentId = root.elementText("学生编号");

      if (courseDao.findById(courseId).isEmpty()) {
        return Message.err(request.requestId(), "NO_SUCH_COURSE",
            "course not found: " + courseId);
      }
      if (choiceDao.exists(studentId, courseId)) {
        return Message.err(request.requestId(), "ALREADY_ENROLLED",
            "student already enrolled in this course");
      }
      choiceDao.enrollLocal(studentId, courseId);
      return Message.ok(request.requestId(), "");
    } catch (XmlException e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD",
          "invalid XML: " + e.getMessage());
    } catch (Exception e) {
      return Message.err(request.requestId(), "INTERNAL_ERROR",
          "enroll failed: " + e.getMessage());
    }
  }
}
