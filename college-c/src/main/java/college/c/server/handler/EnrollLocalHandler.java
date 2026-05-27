package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import college.c.server.CollegeServerConfig;

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
      var doc = XmlIO.parse(request.payload());
      String courseId = doc.getRootElement().elementText("Cno");
      String studentId = doc.getRootElement().elementText("Sno");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossEnroll(request, courseId, studentId);
      }

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
