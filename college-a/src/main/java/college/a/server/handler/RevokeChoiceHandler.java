package college.a.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import org.dom4j.Element;

public class RevokeChoiceHandler implements Handler {
  private final ChoiceDao choiceDao;

  public RevokeChoiceHandler(ChoiceDao choiceDao) {
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      Element root = XmlIO.parse(req.payload()).getRootElement();
      String courseId = root.elementText("courseId");
      String studentId = root.elementText("studentId");

      int rows = choiceDao.withdraw(studentId, courseId);
      return rows == 1
          ? Message.ok(req.requestId(), "")
          : Message.err(req.requestId(), "NO_SUCH_CHOICE", studentId + "/" + courseId);
    } catch (Exception e) {
      return Message.err(req.requestId(), "REVOKE_FAILED", e.getMessage());
    }
  }
}
