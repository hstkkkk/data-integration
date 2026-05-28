package college.a.server.handler;

import cn.edu.di.protocol.Message;
import college.a.dao.StudentDao;
import college.a.xml.StudentAAdapter;

import java.util.List;

public class GetStudentProfileHandler implements Handler {
  private final StudentDao studentDao;

  public GetStudentProfileHandler(StudentDao studentDao) {
    this.studentDao = studentDao;
  }

  @Override
  public Message handle(Message req) {
    String id = req.payload().trim();
    if (id.isEmpty()) return Message.err(req.requestId(), "BAD_REQUEST", "student id is required");
    return studentDao.findById(id)
        .map(row -> Message.ok(req.requestId(), StudentAAdapter.marshal(List.of(row))))
        .orElseGet(() -> Message.err(req.requestId(), "NOT_FOUND", id));
  }
}
