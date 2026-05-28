package college.a.server.handler;

import cn.edu.di.protocol.Message;
import college.a.dao.StudentDao;
import college.a.xml.StudentAAdapter;

public class ListStudentsHandler implements Handler {
  private final StudentDao studentDao;

  public ListStudentsHandler(StudentDao studentDao) {
    this.studentDao = studentDao;
  }

  @Override
  public Message handle(Message req) {
    return Message.ok(req.requestId(), StudentAAdapter.marshal(studentDao.findAll()));
  }
}
