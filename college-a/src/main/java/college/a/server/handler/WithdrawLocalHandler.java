package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlException;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import college.a.server.CollegeServerConfig;
import org.dom4j.Document;
import org.dom4j.Element;

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
      Document doc = XmlIO.parse(request.payload());
      Element root = doc.getRootElement();
      String courseId = root.elementText("课程编号");
      String studentId = root.elementText("学生编号");

      if (!config.isLocalCourse(courseId)) {
        return forwardCrossWithdraw(request, courseId, studentId);
      }

      int rows = choiceDao.withdraw(studentId, courseId);
      if (rows == 0) {
        return Message.err(request.requestId(), "NO_SUCH_CHOICE",
            "no enrollment record found");
      }
      return Message.ok(request.requestId(), "");
    } catch (XmlException e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD",
          "invalid XML: " + e.getMessage());
    } catch (Exception e) {
      return Message.err(request.requestId(), "INTERNAL_ERROR",
          "withdraw failed: " + e.getMessage());
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
