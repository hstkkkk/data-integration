package college.a.server.handler;

import college.a.dao.CourseDao;
import college.a.xml.CourseAAdapter;
import cn.edu.di.protocol.Message;

public class AskCourseInfoHandler implements Handler {

  private final CourseDao courseDao;

  public AskCourseInfoHandler(CourseDao courseDao) {
    this.courseDao = courseDao;
  }

  @Override
  public Message handle(Message request) {
    return Message.ok(request.requestId(), CourseAAdapter.marshal(courseDao.findExportableShared()));
  }
}
