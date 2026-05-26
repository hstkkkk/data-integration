package college.b.server.handler;

import college.b.dao.CourseDao;
import college.b.xml.CourseBAdapter;
import cn.edu.di.protocol.Message;

public class ListLocalCoursesHandler implements Handler {
  private final CourseDao courseDao;
  public ListLocalCoursesHandler(CourseDao courseDao) { this.courseDao = courseDao; }

  @Override
  public Message handle(Message request) {
    try {
      var rows = courseDao.findAll();
      String xml = CourseBAdapter.marshal(rows);
      return Message.ok(request.requestId(), xml);
    } catch (Exception e) {
      return Message.err(request.requestId(), "INTERNAL_ERROR", e.getMessage());
    }
  }
}
