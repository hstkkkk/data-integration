package college.c.xml;

import cn.edu.di.xml.XsdValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchemaCTest {
  XsdValidator load(String name) { return XsdValidator.fromClasspath("/schema/" + name); }

  @Test void student_valid() {
    assertTrue(load("studentC.xsd").validate(
        "<students><student><Sno>CS001</Sno><Snm>张三</Snm><Sex>M</Sex><Sde>CS</Sde></student></students>").valid());
  }
  @Test void class_valid() {
    assertTrue(load("classC.xsd").validate(
        "<courses><course><Cno>CC01</Cno><Cnm>DB</Cnm><Cpt>3</Cpt><Tec>Li</Tec><Pla>C101</Pla><Share>Y</Share></course></courses>").valid());
  }
  @Test void choice_valid() {
    assertTrue(load("choiceC.xsd").validate(
        "<choices><choice><Cno>CC01</Cno><Sno>CS001</Sno></choice></choices>").valid());
  }
}
