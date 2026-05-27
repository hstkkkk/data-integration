package college.c.server.handler;

import college.c.dao.CourseDao;
import college.c.xml.CourseCAdapter;
import cn.edu.di.protocol.Message;

public class AskCourseInfoHandler implements Handler {

  private final CourseDao courseDao;

  public AskCourseInfoHandler(CourseDao courseDao) {
    this.courseDao = courseDao;
  }

  @Override
  public Message handle(Message request) {
    return Message.ok(request.requestId(), CourseCAdapter.marshal(courseDao.findAll()));
  }
}
