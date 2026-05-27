package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.ChoiceDao;
import college.c.server.CollegeServerConfig;

import java.net.Socket;
import java.util.UUID;

public class WithdrawLocalHandler implements Handler {
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public WithdrawLocalHandler(ChoiceDao choiceDao, CollegeServerConfig config) {
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
        return forwardCrossWithdraw(request, courseId, studentId);
      }

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(request.requestId(), "")
          : Message.err(request.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }

  private Message forwardCrossWithdraw(Message req, String courseId, String studentId) {
    String payload = "<crossWithdraw>"
        + "<courseId>" + courseId + "</courseId>"
        + "<studentId>" + studentId + "</studentId>"
        + "<fromCollege>" + config.collegeCode + "</fromCollege>"
        + "</crossWithdraw>";
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.CROSS_WITHDRAW, UUID.randomUUID().toString(), payload));
      return Message.read(sock.getInputStream());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
