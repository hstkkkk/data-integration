package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlException;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.server.CollegeServerConfig;
import org.dom4j.Document;
import org.dom4j.Element;

import java.net.Socket;
import java.util.UUID;

public class EnrollLocalHandler implements Handler {

  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public EnrollLocalHandler(CourseDao courseDao, ChoiceDao choiceDao, CollegeServerConfig config) {
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message request) {
    try {
      Document doc = XmlIO.parse(request.payload());
      Element root = doc.getRootElement();
      String courseId = root.elementText("课程编号");
      String studentId = root.elementText("学生编号");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossEnroll(request, courseId, studentId);
      }

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

  private Message forwardCrossEnroll(Message req, String courseId, String studentId) {
    String payload = "<crossEnroll>"
        + "<courseId>" + courseId + "</courseId>"
        + "<studentId>" + studentId + "</studentId>"
        + "<fromCollege>" + config.collegeCode + "</fromCollege>"
        + "</crossEnroll>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.CROSS_ENROLL, UUID.randomUUID().toString(), payload));
      return Message.read(sock.getInputStream());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
