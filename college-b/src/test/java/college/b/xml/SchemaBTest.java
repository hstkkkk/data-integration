package college.b.xml;

import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemaBTest {
  XsdValidator load(String name) { return XsdValidator.fromClasspath("/schema/" + name); }

  @Test void student_valid() {
    String xml = "<学生集><学生><学号>BS001</学号><姓名>张三</姓名><性别>男</性别><专业>计算机</专业></学生></学生集>";
    assertTrue(load("studentB.xsd").validate(xml).valid());
  }

  @Test void student_missing_field_invalid() {
    String xml = "<学生集><学生><学号>BS001</学号></学生></学生集>";
    assertFalse(load("studentB.xsd").validate(xml).valid());
  }

  @Test void class_valid() {
    String xml = "<课程集><课程><编号>BC001</编号><名称>数据库</名称><学分>3</学分><老师>李老师</老师><地点>B101</地点><共享>Y</共享></课程></课程集>";
    assertTrue(load("classB.xsd").validate(xml).valid());
  }

  @Test void choice_valid() {
    String xml = "<选课集><选课><课程编号>BC001</课程编号><学号>BS001</学号></选课></选课集>";
    assertTrue(load("choiceB.xsd").validate(xml).valid());
  }
}
