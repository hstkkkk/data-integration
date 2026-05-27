package college.b.server.handler;

import college.b.dao.CourseDao;
import college.b.xml.CourseBAdapter;
import cn.edu.di.protocol.Message;

public class AskCourseInfoHandler implements Handler {

  private final CourseDao courseDao;

  public AskCourseInfoHandler(CourseDao courseDao) {
    this.courseDao = courseDao;
  }

  @Override
  public Message handle(Message request) {
    return Message.ok(request.requestId(), CourseBAdapter.marshal(courseDao.findAll()));
  }
}
