package college.c.server.handler;

import college.c.dao.ChoiceDao;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

public class WithdrawLocalHandler implements Handler {
  private final ChoiceDao choiceDao;
  public WithdrawLocalHandler(ChoiceDao choiceDao) { this.choiceDao = choiceDao; }

  @Override
  public Message handle(Message request) {
    try {
      var doc = XmlIO.parse(request.payload());
      String courseId = doc.getRootElement().elementText("Cno");
      String studentId = doc.getRootElement().elementText("Sno");

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(request.requestId(), "")
          : Message.err(request.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
