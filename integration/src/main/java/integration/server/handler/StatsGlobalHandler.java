package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import integration.net.CollegeClient;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class StatsGlobalHandler implements Handler {

  private final CollegeClient clientA;
  private final CollegeClient clientB;
  private final CollegeClient clientC;

  public StatsGlobalHandler(CollegeClient clientA, CollegeClient clientB, CollegeClient clientC) {
    this.clientA = clientA;
    this.clientB = clientB;
    this.clientC = clientC;
  }

  private record CollegeStat(String code, int students, int courses, int shared, int cross) {}
  private record CourseEntry(String id, String name, int enrollments) {}

  @Override
  public Message handle(Message req) {
    try {
      var stats = new ArrayList<CollegeStat>();
      var allCourses = new ArrayList<CourseEntry>();

      for (CollegeClient client : List.of(clientA, clientB, clientC)) {
        Message resp = client.send(new Message(Command.STATS_PULL, UUID.randomUUID().toString(), ""));
        if (resp.command() != Command.OK) {
          return Message.err(req.requestId(), "PULL_FAILED", resp.payload());
        }
        Element root = XmlIO.parse(resp.payload()).getRootElement();
        String code = root.attributeValue("college");
        int students = Integer.parseInt(root.elementText("studentCount"));
        int courses = Integer.parseInt(root.elementText("courseCount"));
        int shared = Integer.parseInt(root.elementText("sharedCount"));
        int cross = Integer.parseInt(root.elementText("crossEnrollmentCount"));
        stats.add(new CollegeStat(code, students, courses, shared, cross));

        Element coursesEl = root.element("courses");
        for (Object o : coursesEl.elements("course")) {
          Element ce = (Element) o;
          allCourses.add(new CourseEntry(
              ce.attributeValue("id"),
              ce.attributeValue("name"),
              Integer.parseInt(ce.attributeValue("enrollments"))));
        }
      }

      int totalStudents = stats.stream().mapToInt(CollegeStat::students).sum();
      int totalCourses = stats.stream().mapToInt(CollegeStat::courses).sum();
      int totalShared = stats.stream().mapToInt(CollegeStat::shared).sum();
      int totalCross = stats.stream().mapToInt(CollegeStat::cross).sum();

      allCourses.sort(Comparator.comparingInt(CourseEntry::enrollments).reversed());
      var top5 = allCourses.stream().limit(5).toList();

      StringBuilder sb = new StringBuilder();
      sb.append("<stats>");
      sb.append("<summary>");
      sb.append("<totalStudents>").append(totalStudents).append("</totalStudents>");
      sb.append("<totalCourses>").append(totalCourses).append("</totalCourses>");
      sb.append("<totalSharedCourses>").append(totalShared).append("</totalSharedCourses>");
      sb.append("<crossEnrollments>").append(totalCross).append("</crossEnrollments>");
      sb.append("</summary>");
      sb.append("<byCollege>");
      for (var s : stats) {
        sb.append("<college code=\"").append(s.code())
          .append("\" students=\"").append(s.students())
          .append("\" courses=\"").append(s.courses())
          .append("\" shared=\"").append(s.shared())
          .append("\" crossEnrollments=\"").append(s.cross())
          .append("\"/>");
      }
      sb.append("</byCollege>");
      sb.append("<topCourses>");
      for (var c : top5) {
        sb.append("<course id=\"").append(c.id())
          .append("\" name=\"").append(c.name())
          .append("\" enrollments=\"").append(c.enrollments())
          .append("\"/>");
      }
      sb.append("</topCourses>");
      sb.append("</stats>");

      return Message.ok(req.requestId(), sb.toString());
    } catch (Exception e) {
      return Message.err(req.requestId(), "STATS_FAILED", e.getMessage());
    }
  }
}
