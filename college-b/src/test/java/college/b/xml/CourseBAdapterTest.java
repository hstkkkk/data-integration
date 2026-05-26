package college.b.xml;

import college.b.dao.CourseDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CourseBAdapterTest {
  @Test void marshal_validates_against_xsd() {
    var rows = List.of(
        new CourseDao.Row("BC001","数据库",32,new BigDecimal("3.0"),"李老师","B101",true));
    var r = XsdValidator.fromClasspath("/schema/classB.xsd").validate(CourseBAdapter.marshal(rows));
    assertTrue(r.valid(), r.errors().toString());
  }
  @Test void roundtrip() throws Exception {
    var original = List.of(
        new CourseDao.Row("BC001","数据库",32,new BigDecimal("3.0"),"李老师","B101",true));
    var parsed = CourseBAdapter.unmarshal(XmlIO.parse(CourseBAdapter.marshal(original)));
    assertEquals("BC001", parsed.get(0).id());
    assertEquals("数据库", parsed.get(0).name());
    assertTrue(parsed.get(0).shared());
  }
}
