package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import integration.net.CollegeClient;
import org.dom4j.Element;
import java.util.*;
import java.util.stream.Collectors;

public class AnalyticsHandler implements Handler {

  private final CollegeClient clientA, clientB, clientC;

  public AnalyticsHandler(CollegeClient ca, CollegeClient cb, CollegeClient cc) {
    this.clientA = ca; this.clientB = cb; this.clientC = cc;
  }

  @Override
  public Message handle(Message req) {
    try {
      int totalStudents = 0, totalCourses = 0, totalShared = 0, totalCross = 0;
      var collegeStats = new ArrayList<ColStat>();
      var allCourses = new ArrayList<CourseRec>();

      for (var entry : Map.of("A", clientA, "B", clientB, "C", clientC).entrySet()) {
        Message resp = entry.getValue().send(
            new Message(Command.STATS_PULL, UUID.randomUUID().toString(), ""));
        if (resp.command() != Command.OK) continue;
        Element root = XmlIO.parse(resp.payload()).getRootElement();
        String code = entry.getKey();
        int s = Integer.parseInt(root.elementText("studentCount"));
        int c = Integer.parseInt(root.elementText("courseCount"));
        int sh = Integer.parseInt(root.elementText("sharedCount"));
        int cr = Integer.parseInt(root.elementText("crossEnrollmentCount"));
        totalStudents += s; totalCourses += c; totalShared += sh; totalCross += cr;
        collegeStats.add(new ColStat(code, s, c, sh, cr));

        Element coursesEl = root.element("courses");
        if (coursesEl != null) {
          for (var o : coursesEl.elements("course")) {
            var ce = (Element) o;
            allCourses.add(new CourseRec(
                ce.attributeValue("id"), ce.attributeValue("name"),
                Integer.parseInt(ce.attributeValue("enrollments")), code));
          }
        }
      }

      allCourses.sort((a, b) -> Integer.compare(b.enrollments, a.enrollments));
      var top10 = allCourses.stream().limit(10).collect(Collectors.toList());

      // Build analyticsReport XML (same format as StatsData.toXml())
      return Message.ok(req.requestId(), buildXml(totalStudents, totalCourses,
          totalShared, totalCross, collegeStats, top10));
    } catch (Exception e) {
      return Message.err(req.requestId(), "ANALYTICS_FAILED", e.getMessage());
    }
  }

  private String buildXml(int totalStudents, int totalCourses, int totalShared,
      int totalCross, List<ColStat> colleges, List<CourseRec> topCourses) {
    StringBuilder sb = new StringBuilder();
    sb.append("<analyticsReport timestamp=\"").append(java.time.Instant.now()).append("\">");
    sb.append("<summary>");
    sb.append("<totalStudents>").append(totalStudents).append("</totalStudents>");
    sb.append("<totalCourses>").append(totalCourses).append("</totalCourses>");
    sb.append("<totalShared>").append(totalShared).append("</totalShared>");
    sb.append("<totalCross>").append(totalCross).append("</totalCross>");
    sb.append("</summary><colleges>");
    for (var c : colleges) {
      sb.append("<college code=\"").append(c.code()).append("\">");
      sb.append("<students>").append(c.students()).append("</students>");
      sb.append("<courses>").append(c.courses()).append("</courses>");
      sb.append("<shared>").append(c.shared()).append("</shared>");
      sb.append("<cross>").append(c.cross()).append("</cross>");
      sb.append("</college>");
    }
    sb.append("</colleges><topCourses>");
    for (var c : topCourses) {
      sb.append("<course id=\"").append(c.id()).append("\" name=\"").append(esc(c.name()))
          .append("\" enrollments=\"").append(c.enrollments()).append("\" origin=\"").append(c.origin()).append("\"/>");
    }
    sb.append("</topCourses></analyticsReport>");
    return sb.toString();
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private record ColStat(String code, int students, int courses, int shared, int cross) {}
  private record CourseRec(String id, String name, int enrollments, String origin) {}
}
