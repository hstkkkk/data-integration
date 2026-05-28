package college.a.xml;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedMyChoiceToAXslTest {
  @Test void unified_to_a_local_keeps_origin_and_sno() {
    var t = XsltTransformer.fromClasspath("/xsl/unifiedMyChoiceToA.xsl");
    String in = "<classes><class origin=\"B\">"
        + "<id>BC003</id><name>OS</name><time>32</time><score>3</score>"
        + "<teacher>Wang</teacher><location>B201</location>"
        + "<share>Y</share><sno>AS001</sno><grade></grade>"
        + "</class></classes>";
    String out = t.transform(in);
    assertTrue(out.contains("<课程编号>BC003</课程编号>"));
    assertTrue(out.contains("<授课老师>Wang</授课老师>"));
    assertTrue(out.contains("<学生编号>AS001</学生编号>"));
    assertTrue(out.contains("<来源>B</来源>"));
  }
}
