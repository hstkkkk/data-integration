package college.a.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlException;
import cn.edu.di.xml.XmlIO;
import college.a.dao.ChoiceDao;
import org.dom4j.Document;
import org.dom4j.Element;

public class WithdrawLocalHandler implements Handler {

  private final ChoiceDao choiceDao;

  public WithdrawLocalHandler(ChoiceDao choiceDao) {
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message request) {
    try {
      Document doc = XmlIO.parse(request.payload());
      Element root = doc.getRootElement();
      String courseId = root.elementText("课程编号");
      String studentId = root.elementText("学生编号");

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
}
