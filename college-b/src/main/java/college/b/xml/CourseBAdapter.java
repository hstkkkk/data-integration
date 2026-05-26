package college.b.xml;

import college.b.dao.CourseDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class CourseBAdapter {
  private CourseBAdapter() {}

  public static String marshal(List<CourseDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("课程集");
    for (var r : rows) {
      Element e = root.addElement("课程");
      e.addElement("编号").setText(r.id());
      e.addElement("名称").setText(r.name());
      e.addElement("课时").setText(Integer.toString(r.hours()));
      e.addElement("学分").setText(r.score().toPlainString());
      e.addElement("老师").setText(r.teacher());
      e.addElement("地点").setText(r.location());
      e.addElement("共享").setText(r.shared() ? "Y" : "N");
    }
    return XmlIO.toPrettyString(doc);
  }

  public static List<CourseDao.Row> unmarshal(Document doc) {
    List<CourseDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("课程")) {
      Element e = (Element) o;
      out.add(new CourseDao.Row(
          e.elementText("编号"),
          e.elementText("名称"),
          parseInt(e.elementText("课时")),
          new BigDecimal(e.elementText("学分")),
          e.elementText("老师"),
          e.elementText("地点"),
          "Y".equalsIgnoreCase(e.elementText("共享"))));
    }
    return out;
  }

  private static int parseInt(String s) { return s == null || s.isBlank() ? 0 : Integer.parseInt(s); }
}
