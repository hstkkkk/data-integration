package college.a.server.handler;

import cn.edu.di.protocol.Message;
import college.a.dao.CourseDao;
import college.a.xml.CourseAAdapter;

public class ListLocalCoursesHandler implements Handler {

  private final CourseDao courseDao;

  public ListLocalCoursesHandler(CourseDao courseDao) {
    this.courseDao = courseDao;
  }

  @Override
  public Message handle(Message request) {
    try {
      var rows = courseDao.findAll();
      var xml = CourseAAdapter.marshal(rows);
      return Message.ok(request.requestId(), xml);
    } catch (Exception e) {
      return Message.err(request.requestId(), "INTERNAL_ERROR",
          "failed to list courses: " + e.getMessage());
    }
  }
}
