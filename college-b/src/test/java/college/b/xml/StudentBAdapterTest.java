package college.b.xml;

import college.b.dao.StudentDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StudentBAdapterTest {
  @Test void marshal_validates_against_xsd() {
    var rows = List.of(new StudentDao.Row("BS001","张三","男","计算机","pw1"));
    var r = XsdValidator.fromClasspath("/schema/studentB.xsd").validate(StudentBAdapter.marshal(rows));
    assertTrue(r.valid(), r.errors().toString());
  }
  @Test void roundtrip() throws Exception {
    var original = List.of(new StudentDao.Row("BS001","张三","男","计算机","pw1"));
    var parsed = StudentBAdapter.unmarshal(XmlIO.parse(StudentBAdapter.marshal(original)));
    assertEquals("张三", parsed.get(0).name());
    assertEquals("计算机", parsed.get(0).major());
  }
}
