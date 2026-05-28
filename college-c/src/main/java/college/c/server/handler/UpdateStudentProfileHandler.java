package college.c.server.handler;

import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import college.c.dao.StudentDao;

public class UpdateStudentProfileHandler implements Handler {
  private final StudentDao studentDao;

  public UpdateStudentProfileHandler(StudentDao studentDao) {
    this.studentDao = studentDao;
  }

  @Override
  public Message handle(Message req) {
    try {
      var root = XmlIO.parse(req.payload()).getRootElement();
      String id = root.elementText("Sno");
      boolean ok = studentDao.updateProfile(id, root.elementText("Snm"),
          root.elementText("Sex"), root.elementText("Sde"));
      return ok ? Message.ok(req.requestId(), "<updated/>")
          : Message.err(req.requestId(), "NOT_FOUND", id);
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_XML", e.getMessage());
    }
  }
}
