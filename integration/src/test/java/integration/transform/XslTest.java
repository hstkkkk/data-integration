package integration.transform;

import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XslTest {
  @Test void formatA_converts_to_unified() {
    var t = XsltTransformer.fromClasspath("/xsl/formatA.xsl");
    String in = "<课程集><课程><课程编号>AC001</课程编号><课程名称>DB</课程名称><课时>32</课时><学分>3</学分><授课老师>Li</授课老师><授课地点>A101</授课地点><共享>Y</共享></课程></课程集>";
    String out = t.transform(in);
    assertTrue(out.contains("<id>AC001</id>"));
    assertTrue(out.contains("<origin>A</origin>"));
  }

  @Test void formatB_converts_to_unified() {
    var t = XsltTransformer.fromClasspath("/xsl/formatB.xsl");
    String in = "<课程集><课程><编号>BC001</编号><名称>OS</名称><课时>48</课时><学分>4</学分><老师>Wang</老师><地点>B201</地点><共享>Y</共享></课程></课程集>";
    String out = t.transform(in);
    assertTrue(out.contains("<id>BC001</id>"));
    assertTrue(out.contains("<origin>B</origin>"));
  }

  @Test void formatC_converts_to_unified() {
    var t = XsltTransformer.fromClasspath("/xsl/formatC.xsl");
    String in = "<courses><course><Cno>CC01</Cno><Cnm>Net</Cnm><Ctm>32</Ctm><Cpt>2</Cpt><Tec>Li</Tec><Pla>C301</Pla><Share>Y</Share></course></courses>";
    String out = t.transform(in);
    assertTrue(out.contains("<id>CC01</id>"));
    assertTrue(out.contains("<origin>C</origin>"));
  }

  @Test void identity_preserves_xml() {
    var t = XsltTransformer.fromClasspath("/xsl/identity.xsl");
    assertTrue(t.transform("<a>1</a>").contains("<a>1</a>"));
  }
}
