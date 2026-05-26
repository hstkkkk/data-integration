package college.c.xml;

import college.c.dao.ChoiceDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import java.util.ArrayList;
import java.util.List;

public final class ChoiceCAdapter {
  private ChoiceCAdapter() {}

  public static String marshal(List<ChoiceDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("choices");
    for (var r : rows) {
      Element e = root.addElement("choice");
      e.addElement("Cno").setText(r.courseId());
      e.addElement("Sno").setText(r.studentId());
      if (r.grade() != null) e.addElement("Grd").setText(r.grade());
    }
    return XmlIO.toPrettyString(doc);
  }

  public static List<ChoiceDao.Row> unmarshal(Document doc) {
    List<ChoiceDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("choice")) {
      Element e = (Element) o;
      out.add(new ChoiceDao.Row(e.elementText("Cno"), e.elementText("Sno"), e.elementText("Grd")));
    }
    return out;
  }
}
