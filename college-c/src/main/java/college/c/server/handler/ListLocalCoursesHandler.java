package college.c.server.handler;

import college.c.dao.CourseDao;
import college.c.xml.CourseCAdapter;
import cn.edu.di.protocol.Message;

public class ListLocalCoursesHandler implements Handler {
  private final CourseDao courseDao;
  public ListLocalCoursesHandler(CourseDao courseDao) { this.courseDao = courseDao; }

  @Override
  public Message handle(Message request) {
    try {
      return Message.ok(request.requestId(), CourseCAdapter.marshal(courseDao.findAll()));
    } catch (Exception e) {
      return Message.err(request.requestId(), "INTERNAL_ERROR", e.getMessage());
    }
  }
}
