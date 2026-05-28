package analytics.model;

import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.Element;
import java.util.ArrayList;
import java.util.List;

public record StatsData(Summary summary, List<CollegeStat> colleges, List<CourseEntry> topCourses) {

  public record Summary(int totalStudents, int totalCourses, int totalShared, int totalCross) {}
  public record CollegeStat(String code, int students, int courses, int shared, int cross) {}
  public record CourseEntry(String id, String name, int enrollments, String origin) {}

  public static StatsData fromXml(Document doc) {
    Element root = doc.getRootElement();
    Element s = root.element("summary");
    Summary summary = new Summary(
        Integer.parseInt(s.elementText("totalStudents")),
        Integer.parseInt(s.elementText("totalCourses")),
        Integer.parseInt(s.elementText("totalShared")),
        Integer.parseInt(s.elementText("totalCross")));

    List<CollegeStat> colleges = new ArrayList<>();
    for (Object o : root.element("colleges").elements("college")) {
      Element e = (Element) o;
      colleges.add(new CollegeStat(e.attributeValue("code"),
          Integer.parseInt(e.elementText("students")),
          Integer.parseInt(e.elementText("courses")),
          Integer.parseInt(e.elementText("shared")),
          Integer.parseInt(e.elementText("cross"))));
    }

    List<CourseEntry> top = new ArrayList<>();
    Element tc = root.element("topCourses");
    if (tc != null) {
      for (Object o : tc.elements("course")) {
        Element e = (Element) o;
        top.add(new CourseEntry(e.attributeValue("id"), e.attributeValue("name"),
            Integer.parseInt(e.attributeValue("enrollments")), e.attributeValue("origin")));
      }
    }
    return new StatsData(summary, colleges, top);
  }

  public String toXml() {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    sb.append("<analyticsReport timestamp=\"").append(java.time.Instant.now()).append("\">");
    sb.append("<summary>");
    sb.append("<totalStudents>").append(summary.totalStudents()).append("</totalStudents>");
    sb.append("<totalCourses>").append(summary.totalCourses()).append("</totalCourses>");
    sb.append("<totalShared>").append(summary.totalShared()).append("</totalShared>");
    sb.append("<totalCross>").append(summary.totalCross()).append("</totalCross>");
    sb.append("</summary>");
    sb.append("<colleges>");
    for (var c : colleges) {
      sb.append("<college code=\"").append(c.code()).append("\">");
      sb.append("<students>").append(c.students()).append("</students>");
      sb.append("<courses>").append(c.courses()).append("</courses>");
      sb.append("<shared>").append(c.shared()).append("</shared>");
      sb.append("<cross>").append(c.cross()).append("</cross>");
      sb.append("</college>");
    }
    sb.append("</colleges>");
    sb.append("<topCourses>");
    for (var c : topCourses) {
      sb.append("<course id=\"").append(c.id()).append("\" name=\"").append(esc(c.name()))
        .append("\" enrollments=\"").append(c.enrollments()).append("\" origin=\"").append(c.origin()).append("\"/>");
    }
    sb.append("</topCourses>");
    sb.append("</analyticsReport>");
    return sb.toString();
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
