package college.b.server.handler;

import college.b.dao.ChoiceDao;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

public class WithdrawLocalHandler implements Handler {
  private final ChoiceDao choiceDao;
  public WithdrawLocalHandler(ChoiceDao choiceDao) { this.choiceDao = choiceDao; }

  @Override
  public Message handle(Message request) {
    try {
      var doc = XmlIO.parse(request.payload());
      String courseId = doc.getRootElement().elementText("课程编号");
      String studentId = doc.getRootElement().elementText("学号");

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(request.requestId(), "")
          : Message.err(request.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
