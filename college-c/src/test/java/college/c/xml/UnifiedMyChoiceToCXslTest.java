package college.c.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedMyChoiceToCXslTest {
  @Test void unified_to_c_local_keeps_origin_and_sno() {
    var t = XsltTransformer.fromClasspath("/xsl/unifiedMyChoiceToC.xsl");
    String in = "<classes><class origin=\"A\">"
        + "<id>AC001</id><name>Math</name><time>48</time><score>3</score>"
        + "<teacher>Zhao</teacher><location>A101</location>"
        + "<share>Y</share><sno>CS001</sno><grade></grade>"
        + "</class></classes>";
    String out = t.transform(in);
    assertTrue(out.contains("<Cno>AC001</Cno>"));
    assertTrue(out.contains("<Tec>Zhao</Tec>"));
    assertTrue(out.contains("<Sno>CS001</Sno>"));
    assertTrue(out.contains("<Org>A</Org>"));
  }
}
