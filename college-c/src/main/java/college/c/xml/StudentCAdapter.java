package college.c.xml;

import college.c.dao.StudentDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import java.util.ArrayList;
import java.util.List;

public final class StudentCAdapter {
  private StudentCAdapter() {}

  public static String marshal(List<StudentDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("students");
    for (var r : rows) {
      Element e = root.addElement("student");
      e.addElement("Sno").setText(r.id());
      e.addElement("Snm").setText(r.name());
      e.addElement("Sex").setText(r.sex());
      e.addElement("Sde").setText(r.dept());
      if (r.password() != null) e.addElement("Pwd").setText(r.password());
    }
    return XmlIO.toPrettyString(doc);
  }

  public static List<StudentDao.Row> unmarshal(Document doc) {
    List<StudentDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("student")) {
      Element e = (Element) o;
      out.add(new StudentDao.Row(e.elementText("Sno"), e.elementText("Snm"),
          e.elementText("Sex"), e.elementText("Sde"), e.elementText("Pwd")));
    }
    return out;
  }
}
