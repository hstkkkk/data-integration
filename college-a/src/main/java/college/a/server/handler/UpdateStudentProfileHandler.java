package college.a.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.a.dao.StudentDao;

public class UpdateStudentProfileHandler implements Handler {
  private final StudentDao studentDao;

  public UpdateStudentProfileHandler(StudentDao studentDao) {
    this.studentDao = studentDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      var root = XmlIO.parse(req.payload()).getRootElement();
      String id = root.elementText("学号");
      boolean ok = studentDao.updateProfile(id, root.elementText("姓名"),
          root.elementText("性别"), root.elementText("院系"));
      return ok ? Message.ok(req.requestId(), "<updated/>")
          : Message.err(req.requestId(), "NOT_FOUND", id);
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_XML", e.getMessage());
    }
  }
}
