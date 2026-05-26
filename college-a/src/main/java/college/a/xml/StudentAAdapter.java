// college-a/src/main/java/college/a/xml/StudentAAdapter.java
package college.a.xml;

import college.a.dao.StudentDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import java.util.ArrayList;
import java.util.List;

public final class StudentAAdapter {
  private StudentAAdapter() {}

  public static String marshal(List<StudentDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("学生集");
    for (var r : rows) {
      Element e = root.addElement("学生");
      e.addElement("学号").setText(r.id());
      e.addElement("姓名").setText(r.name());
      e.addElement("性别").setText(r.sex());
      e.addElement("院系").setText(r.dept());
      if (r.accountRef() != null) e.addElement("关联账户").setText(r.accountRef());
    }
    return XmlIO.toPrettyString(doc);
  }

  public static List<StudentDao.Row> unmarshal(Document doc) {
    List<StudentDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("学生")) {
      Element e = (Element) o;
      out.add(new StudentDao.Row(
          e.elementText("学号"), e.elementText("姓名"),
          e.elementText("性别"), e.elementText("院系"),
          e.elementText("关联账户")));
    }
    return out;
  }
}
