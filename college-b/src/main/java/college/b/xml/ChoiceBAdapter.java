package college.b.xml;

import college.b.dao.ChoiceDao;
import cn.edu.di.xml.XmlIO;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import java.util.ArrayList;
import java.util.List;

public final class ChoiceBAdapter {
  private ChoiceBAdapter() {}

  public static String marshal(List<ChoiceDao.Row> rows) {
    Document doc = DocumentHelper.createDocument();
    Element root = doc.addElement("选课集");
    for (var r : rows) {
      Element e = root.addElement("选课");
      e.addElement("课程编号").setText(r.courseId());
      e.addElement("学号").setText(r.studentId());
      if (r.score() != null) e.addElement("得分").setText(r.score());
    }
    return XmlIO.toPrettyString(doc);
  }

  public static List<ChoiceDao.Row> unmarshal(Document doc) {
    List<ChoiceDao.Row> out = new ArrayList<>();
    for (Object o : doc.getRootElement().elements("选课")) {
      Element e = (Element) o;
      out.add(new ChoiceDao.Row(
          e.elementText("课程编号"),
          e.elementText("学号"),
          e.elementText("得分")));
    }
    return out;
  }
}
