package college.c.xml;

import college.c.dao.CourseDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class CourseCAdapter {
  private CourseCAdapter() {}

  public static String marshal(List<CourseDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("courses");
    for (var r : rows) {
      Element e = root.addElement("course");
      e.addElement("Cno").setText(r.id());
      e.addElement("Cnm").setText(r.name());
      e.addElement("Ctm").setText(Integer.toString(r.hours()));
      e.addElement("Cpt").setText(r.score().toPlainString());
      e.addElement("Tec").setText(r.teacher());
      e.addElement("Pla").setText(r.location());
      e.addElement("Share").setText(r.shared() ? "Y" : "N");
    }
    return XmlIO.toPrettyString(doc);
  }

  public static List<CourseDao.Row> unmarshal(Document doc) {
    List<CourseDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("course")) {
      Element e = (Element) o;
      out.add(new CourseDao.Row(e.elementText("Cno"), e.elementText("Cnm"),
          parseInt(e.elementText("Ctm")), new BigDecimal(e.elementText("Cpt")),
          e.elementText("Tec"), e.elementText("Pla"),
          "Y".equalsIgnoreCase(e.elementText("Share"))));
    }
    return out;
  }

  private static int parseInt(String s) { return s == null || s.isBlank() ? 0 : Integer.parseInt(s); }
}
