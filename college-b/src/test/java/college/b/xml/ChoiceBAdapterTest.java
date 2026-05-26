package college.b.xml;

import college.b.dao.ChoiceDao;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChoiceBAdapterTest {
  @Test void marshal_validates() {
    var rows = List.of(new ChoiceDao.Row("BC001","BS001","90"));
    var r = XsdValidator.fromClasspath("/schema/choiceB.xsd").validate(ChoiceBAdapter.marshal(rows));
    assertTrue(r.valid(), r.errors().toString());
  }
  @Test void roundtrip() throws Exception {
    var original = List.of(new ChoiceDao.Row("BC001","BS001","90"));
    var parsed = ChoiceBAdapter.unmarshal(XmlIO.parse(ChoiceBAdapter.marshal(original)));
    assertEquals("BS001", parsed.get(0).studentId());
    assertEquals("90", parsed.get(0).score());
  }
}
