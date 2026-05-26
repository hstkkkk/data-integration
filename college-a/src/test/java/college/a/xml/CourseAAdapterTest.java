// college-a/src/test/java/college/a/xml/CourseAAdapterTest.java
package college.a.xml;

import college.a.dao.CourseDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseAAdapterTest {
  @Test
  void marshal_then_validate_against_xsd() throws Exception {
    var rows = List.of(
        new CourseDao.Row("AC001", "数据库", 32, new BigDecimal("3.0"), "李老师", "A101", true),
        new CourseDao.Row("AC002", "编译", 48, new BigDecimal("4.0"), "王老师", "A102", false));
    String xml = CourseAAdapter.marshal(rows);
    var r = XsdValidator.fromClasspath("/schema/classA.xsd").validate(xml);
    assertTrue(r.valid(), r.errors().toString());
  }

  @Test
  void roundtrip_preserves_data() throws Exception {
    var original = List.of(
        new CourseDao.Row("AC001", "数据库", 32, new BigDecimal("3.0"), "李老师", "A101", true));
    String xml = CourseAAdapter.marshal(original);
    var parsed = CourseAAdapter.unmarshal(XmlIO.parse(xml));
    assertEquals(1, parsed.size());
    assertEquals("AC001", parsed.get(0).id());
    assertEquals("数据库", parsed.get(0).name());
    assertTrue(parsed.get(0).shared());
  }
}
