package college.c.server.handler;

import cn.edu.di.protocol.Message;
import college.c.dao.StudentDao;
import college.c.xml.StudentCAdapter;

public class ListStudentsHandler implements Handler {
  private final StudentDao studentDao;

  public ListStudentsHandler(StudentDao studentDao) {
    this.studentDao = studentDao;
  }

  @Override
  public Message handle(Message req) {
    return Message.ok(req.requestId(), StudentCAdapter.marshal(studentDao.findAll()));
  }
}
