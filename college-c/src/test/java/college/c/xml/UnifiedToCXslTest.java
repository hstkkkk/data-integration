package college.c.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedToCXslTest {
  @Test void unified_converts_to_c_local() {
    var toC = XsltTransformer.fromClasspath("/xsl/unifiedToC.xsl");
    String unified = "<classes><class>"
        + "<id>AC001</id><name>DB</name><time>32</time><score>3</score>"
        + "<teacher>Li</teacher><location>A101</location><share>Y</share><origin>A</origin>"
        + "</class></classes>";
    String out = toC.transform(unified);
    assertTrue(out.contains("<Cno>AC001</Cno>"));
    assertTrue(out.contains("<Tec>Li</Tec>"));
  }
}
