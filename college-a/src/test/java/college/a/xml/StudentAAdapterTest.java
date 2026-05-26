// college-a/src/test/java/college/a/xml/StudentAAdapterTest.java
package college.a.xml;

import college.a.dao.StudentDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StudentAAdapterTest {
  @Test
  void marshal_validates_against_xsd() {
    var rows = List.of(
        new StudentDao.Row("AS001", "张三", "男", "计算机", "as001"),
        new StudentDao.Row("AS002", "李四", "女", "计算机", null));
    String xml = StudentAAdapter.marshal(rows);
    var r = XsdValidator.fromClasspath("/schema/studentA.xsd").validate(xml);
    assertTrue(r.valid(), r.errors().toString());
  }

  @Test
  void roundtrip_preserves() throws Exception {
    var original = List.of(new StudentDao.Row("AS001", "张三", "男", "计算机", "as001"));
    var parsed = StudentAAdapter.unmarshal(XmlIO.parse(StudentAAdapter.marshal(original)));
    assertEquals("张三", parsed.get(0).name());
    assertEquals("as001", parsed.get(0).accountRef());
  }
}
