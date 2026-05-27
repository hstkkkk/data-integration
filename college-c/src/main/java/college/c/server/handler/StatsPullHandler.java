package college.c.server.handler;

import cn.edu.di.protocol.Message;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import college.c.dao.StudentDao;
import college.c.server.CollegeServerConfig;

import java.util.HashMap;
import java.util.Map;

public class StatsPullHandler implements Handler {
  private final StudentDao studentDao;
  private final CourseDao courseDao;
  private final ChoiceDao choiceDao;
  private final CollegeServerConfig config;

  public StatsPullHandler(StudentDao studentDao, CourseDao courseDao,
                          ChoiceDao choiceDao, CollegeServerConfig config) {
    this.studentDao = studentDao;
    this.courseDao = courseDao;
    this.choiceDao = choiceDao;
    this.config = config;
  }

  @Override
  public Message handle(Message req) {
    try {
      var students = studentDao.findAll();
      var courses = courseDao.findAll();
      var shared = courseDao.findShared();
      var choices = choiceDao.findAll();

      Map<String, Integer> enrollByCourse = new HashMap<>();
      int crossCount = 0;
      for (var ch : choices) {
        enrollByCourse.merge(ch.courseId(), 1, Integer::sum);
        if (!ch.studentId().startsWith(config.studentIdPrefix)) crossCount++;
      }

      StringBuilder sb = new StringBuilder();
      sb.append("<pullData college=\"").append(config.collegeCode).append("\">");
      sb.append("<studentCount>").append(students.size()).append("</studentCount>");
      sb.append("<courseCount>").append(courses.size()).append("</courseCount>");
      sb.append("<sharedCount>").append(shared.size()).append("</sharedCount>");
      sb.append("<crossEnrollmentCount>").append(crossCount).append("</crossEnrollmentCount>");
      sb.append("<courses>");
      for (var c : courses) {
        sb.append("<course id=\"").append(c.id())
          .append("\" name=\"").append(c.name())
          .append("\" enrollments=\"").append(enrollByCourse.getOrDefault(c.id(), 0))
          .append("\"/>");
      }
      sb.append("</courses>");
      sb.append("</pullData>");

      return Message.ok(req.requestId(), sb.toString());
    } catch (Exception e) {
      return Message.err(req.requestId(), "PULL_FAILED", e.getMessage());
    }
  }
}
