package integration.transform;

import cn.edu.di.xml.XsdValidator;
import cn.edu.di.xml.XsltTransformer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedPdfXslTest {

  private static final String UNIFIED_CLASS = """
      <classes><class><id>AC001</id><name>DB</name><time>32</time><score>3</score>
      <teacher>Li</teacher><location>A101</location><share>Y</share><origin>A</origin></class></classes>
      """;

  private static final String UNIFIED_STUDENT = """
      <students><student><id>AS001</id><name>Alice</name><sex>女</sex><major>CS</major><origin>A</origin></student></students>
      """;

  private static final String UNIFIED_CHOICE = """
      <choices><choice><sid>AS001</sid><cid>BC001</cid><score>90</score><originStudent>A</originStudent><originCourse>B</originCourse></choice></choices>
      """;

  @Test
  void formatClass_converts_all_college_course_shapes_to_unified_schema() {
    assertValid("/xsl/formatClass.xsl",
        "<课程集><课程><课程编号>AC001</课程编号><课程名称>DB</课程名称><课时>32</课时><学分>3</学分><授课老师>Li</授课老师><授课地点>A101</授课地点><共享>Y</共享></课程></课程集>",
        "/schema/formatClass.xsd");
    assertValid("/xsl/formatClass.xsl",
        "<课程集><课程><编号>BC001</编号><名称>OS</名称><课时>48</课时><学分>4</学分><老师>Wang</老师><地点>B201</地点><共享>Y</共享></课程></课程集>",
        "/schema/formatClass.xsd");
    assertValid("/xsl/formatClass.xsl",
        "<courses><course><Cno>CC01</Cno><Cnm>AI</Cnm><Ctm>40</Ctm><Cpt>3</Cpt><Tec>Zhao</Tec><Pla>C301</Pla><Share>Y</Share></course></courses>",
        "/schema/formatClass.xsd");
  }

  @Test
  void formatStudent_converts_all_college_student_shapes_to_unified_schema() {
    assertValid("/xsl/formatStudent.xsl",
        "<学生集><学生><学号>AS001</学号><姓名>Alice</姓名><性别>女</性别><院系>CS</院系><关联账户>as001</关联账户></学生></学生集>",
        "/schema/formatStudent.xsd");
    assertValid("/xsl/formatStudent.xsl",
        "<学生集><学生><学号>BS001</学号><姓名>Bob</姓名><性别>男</性别><专业>SE</专业><密码>pwd001</密码></学生></学生集>",
        "/schema/formatStudent.xsd");
    assertValid("/xsl/formatStudent.xsl",
        "<students><student><Sno>CS001</Sno><Snm>Chen</Snm><Sex>男</Sex><Sde>AI</Sde><Pwd>pwd001</Pwd></student></students>",
        "/schema/formatStudent.xsd");
  }

  @Test
  void formatClassChoice_converts_all_college_choice_shapes_to_unified_schema() {
    assertValid("/xsl/formatClassChoice.xsl",
        "<选课集><选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号><成绩>91</成绩></选课></选课集>",
        "/schema/formatChoice.xsd");
    assertValid("/xsl/formatClassChoice.xsl",
        "<选课集><选课><课程编号>BC001</课程编号><学号>BS001</学号><得分>88</得分></选课></选课集>",
        "/schema/formatChoice.xsd");
    assertValid("/xsl/formatClassChoice.xsl",
        "<choices><choice><Cno>CC01</Cno><Sno>CS001</Sno><Grd>86</Grd></choice></choices>",
        "/schema/formatChoice.xsd");
  }

  @Test
  void unified_to_target_xsl_files_emit_target_roots() {
    assertRoot("/xsl/classToA.xsl", UNIFIED_CLASS, "课程集");
    assertRoot("/xsl/classToB.xsl", UNIFIED_CLASS, "课程集");
    assertRoot("/xsl/classToC.xsl", UNIFIED_CLASS, "courses");
    assertRoot("/xsl/studentToA.xsl", UNIFIED_STUDENT, "学生集");
    assertRoot("/xsl/studentToB.xsl", UNIFIED_STUDENT, "学生集");
    assertRoot("/xsl/studentToC.xsl", UNIFIED_STUDENT, "students");
    assertRoot("/xsl/choiceToA.xsl", UNIFIED_CHOICE, "选课集");
    assertRoot("/xsl/choiceToB.xsl", UNIFIED_CHOICE, "选课集");
    assertRoot("/xsl/choiceToC.xsl", UNIFIED_CHOICE, "choices");
  }

  private static void assertValid(String xsl, String xml, String schema) {
    String out = XsltTransformer.fromClasspath(xsl).transform(xml);
    var result = XsdValidator.fromClasspath(schema).validate(out);
    assertTrue(result.valid(), () -> xsl + " output should validate: " + result);
  }

  private static void assertRoot(String xsl, String xml, String root) {
    String out = XsltTransformer.fromClasspath(xsl).transform(xml);
    assertTrue(out.contains("<" + root + ">"), () -> xsl + " should emit <" + root + ">: " + out);
  }
}
