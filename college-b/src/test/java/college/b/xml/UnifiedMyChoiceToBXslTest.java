package college.b.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedMyChoiceToBXslTest {
  @Test void unified_to_b_local_keeps_origin_and_sno() {
    var t = XsltTransformer.fromClasspath("/xsl/unifiedMyChoiceToB.xsl");
    String in = "<classes><class origin=\"A\">"
        + "<id>AC001</id><name>Math</name><time>48</time><score>3</score>"
        + "<teacher>Zhao</teacher><location>A101</location>"
        + "<share>Y</share><sno>BS001</sno><grade></grade>"
        + "</class></classes>";
    String out = t.transform(in);
    assertTrue(out.contains("<编号>AC001</编号>"));
    assertTrue(out.contains("<老师>Zhao</老师>"));
    assertTrue(out.contains("<学号>BS001</学号>"));
    assertTrue(out.contains("<来源>A</来源>"));
  }
}
