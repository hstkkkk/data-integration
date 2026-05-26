package college.a.xml;

import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemaATest {
  XsdValidator load(String name) {
    return XsdValidator.fromClasspath("/schema/" + name);
  }

  @Test
  void student_valid() {
    String xml = """
      <学生集>
        <学生><学号>AS001</学号><姓名>张三</姓名><性别>男</性别><院系>计算机</院系><关联账户>as001</关联账户></学生>
      </学生集>""";
    var r = load("studentA.xsd").validate(xml);
    assertTrue(r.valid(), r.errors().toString());
  }

  @Test
  void student_missing_field_invalid() {
    String xml = "<学生集><学生><学号>AS001</学号></学生></学生集>";
    assertFalse(load("studentA.xsd").validate(xml).valid());
  }

  @Test
  void class_valid() {
    String xml = """
      <课程集>
        <课程><课程编号>AC001</课程编号><课程名称>数据库</课程名称><学分>3</学分>
              <授课老师>李老师</授课老师><授课地点>A101</授课地点><共享>Y</共享></课程>
      </课程集>""";
    assertTrue(load("classA.xsd").validate(xml).valid());
  }

  @Test
  void choice_valid() {
    String xml = """
      <选课集>
        <选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号><成绩>90</成绩></选课>
      </选课集>""";
    assertTrue(load("choiceA.xsd").validate(xml).valid());
  }
}
