// college-a/src/test/java/college/a/xml/ChoiceAAdapterTest.java
package college.a.xml;

import college.a.dao.ChoiceDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChoiceAAdapterTest {
  @Test
  void marshal_validates() {
    var rows = List.of(new ChoiceDao.Row("AC001", "AS001", "90", "A"),
                       new ChoiceDao.Row("AC002", "AS002", null, "A"));
    String xml = ChoiceAAdapter.marshal(rows);
    var r = XsdValidator.fromClasspath("/schema/choiceA.xsd").validate(xml);
    assertTrue(r.valid(), r.errors().toString());
  }

  @Test
  void roundtrip() throws Exception {
    var original = List.of(new ChoiceDao.Row("AC001", "AS001", "90", "A"));
    var parsed = ChoiceAAdapter.unmarshal(XmlIO.parse(ChoiceAAdapter.marshal(original)));
    assertEquals("AS001", parsed.get(0).studentId());
    assertEquals("90", parsed.get(0).score());
  }
}
