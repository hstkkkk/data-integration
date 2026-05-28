package college.b.server.handler;

import cn.edu.di.protocol.Message;
import college.b.dao.StudentDao;
import college.b.xml.StudentBAdapter;

public class ListStudentsHandler implements Handler {
  private final StudentDao studentDao;

  public ListStudentsHandler(StudentDao studentDao) {
    this.studentDao = studentDao;
  }

  @Override
  public Message handle(Message req) {
    return Message.ok(req.requestId(), StudentBAdapter.marshal(studentDao.findAll()));
  }
}
