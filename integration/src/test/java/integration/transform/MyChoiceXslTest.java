package integration.transform;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyChoiceXslTest {
  @Test void formatA_myChoice_translates_to_unified_classes() {
    var t = XsltTransformer.fromClasspath("/xsl/formatA-myChoice.xsl");
    String in = "<myChoiceSet sno=\"AS001\">"
        + "<课程><课程编号>AC001</课程编号><课程名称>Math</课程名称>"
        + "<课时>48</课时><学分>3</学分>"
        + "<授课老师>Zhao</授课老师><授课地点>A101</授课地点>"
        + "<学生编号>AS001</学生编号><成绩></成绩></课程>"
        + "</myChoiceSet>";
    String out = t.transform(in);
    assertTrue(out.contains("<class origin=\"A\">"));
    assertTrue(out.contains("<id>AC001</id>"));
    assertTrue(out.contains("<sno>AS001</sno>"));
    assertTrue(out.contains("<grade></grade>") || out.contains("<grade/>"));
  }
}
