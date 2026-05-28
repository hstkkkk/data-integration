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

  @Test void formatB_myChoice_translates_to_unified_classes() {
    var t = XsltTransformer.fromClasspath("/xsl/formatB-myChoice.xsl");
    String in = "<myChoiceSet sno=\"BS001\">"
        + "<课程><编号>BC003</编号><名称>OS</名称>"
        + "<课时>32</课时><学分>3</学分>"
        + "<老师>Wang</老师><地点>B201</地点>"
        + "<学号>BS001</学号><得分></得分></课程>"
        + "</myChoiceSet>";
    String out = t.transform(in);
    assertTrue(out.contains("<class origin=\"B\">"));
    assertTrue(out.contains("<id>BC003</id>"));
    assertTrue(out.contains("<sno>BS001</sno>"));
  }

  @Test void formatC_myChoice_translates_to_unified_classes() {
    var t = XsltTransformer.fromClasspath("/xsl/formatC-myChoice.xsl");
    String in = "<myChoiceSet sno=\"CS001\">"
        + "<course><Cno>CC005</Cno><Cnm>Net</Cnm>"
        + "<Ctm>32</Ctm><Cpt>2</Cpt>"
        + "<Tec>Li</Tec><Pla>C301</Pla>"
        + "<Sno>CS001</Sno><Grd></Grd></course>"
        + "</myChoiceSet>";
    String out = t.transform(in);
    assertTrue(out.contains("<class origin=\"C\">"));
    assertTrue(out.contains("<id>CC005</id>"));
    assertTrue(out.contains("<sno>CS001</sno>"));
  }
}
