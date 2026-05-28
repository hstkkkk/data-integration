package cn.edu.di.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyChoiceSchemaTest {
  private static final String VALID =
      "<classes>"
      + "<class origin=\"B\">"
      + "<id>BC003</id><name>OS</name><time>32</time><score>3.0</score>"
      + "<teacher>Wang</teacher><location>B201</location>"
      + "<share>Y</share><sno>AS001</sno><grade></grade>"
      + "</class></classes>";

  @Test
  void accepts_valid_my_choice_xml() {
    var v = XsdValidator.fromClasspath("/schema/formatMyChoice.xsd");
    var r = v.validate(VALID);
    assertTrue(r.valid(), () -> "errors: " + r.errors());
  }

  @Test
  void rejects_when_sno_missing() {
    String bad = VALID.replace("<sno>AS001</sno>", "");
    var v = XsdValidator.fromClasspath("/schema/formatMyChoice.xsd");
    assertFalse(v.validate(bad).valid());
  }
}
