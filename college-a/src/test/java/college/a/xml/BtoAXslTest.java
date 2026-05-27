package college.a.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BtoAXslTest {
  @Test void unified_converts_to_a_local() {
    var toA = XsltTransformer.fromClasspath("/xsl/BtoA.xsl");
    String unified = "<classes><class>"
        + "<id>BC001</id><name>OS</name><time>48</time><score>4</score>"
        + "<teacher>Wang</teacher><location>B201</location><share>Y</share><origin>B</origin>"
        + "</class></classes>";
    String out = toA.transform(unified);
    assertTrue(out.contains("<课程编号>BC001</课程编号>"));
    assertTrue(out.contains("<授课老师>Wang</授课老师>"));
  }
}
