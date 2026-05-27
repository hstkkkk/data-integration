package college.b.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtoBXslTest {
  @Test void unified_converts_to_b_local() {
    var toB = XsltTransformer.fromClasspath("/xsl/AtoB.xsl");
    String unified = "<classes><class>"
        + "<id>AC001</id><name>DB</name><time>32</time><score>3</score>"
        + "<teacher>Li</teacher><location>A101</location><share>Y</share><origin>A</origin>"
        + "</class></classes>";
    String out = toB.transform(unified);
    assertTrue(out.contains("<编号>AC001</编号>"));
    assertTrue(out.contains("<老师>Li</老师>"));
  }
}
