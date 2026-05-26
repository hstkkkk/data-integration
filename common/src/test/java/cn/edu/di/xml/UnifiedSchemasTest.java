package cn.edu.di.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedSchemasTest {

    @Test
    void validClass_passes() {
        var v = XsdValidator.fromClasspath("/schema/formatClass.xsd");
        assertTrue(v.validate(
            "<classes><class>" +
            "<id>A001</id><name>DB</name><time>40</time><score>3</score>" +
            "<teacher>Li</teacher><location>R1</location><share>Y</share><origin>A</origin>" +
            "</class></classes>").valid());
    }

    @Test
    void classMissingShare_fails() {
        var v = XsdValidator.fromClasspath("/schema/formatClass.xsd");
        assertFalse(v.validate(
            "<classes><class><id>A001</id><name>DB</name><time>40</time>" +
            "<score>3</score><teacher>Li</teacher><location>R1</location><origin>A</origin>" +
            "</class></classes>").valid());
    }

    @Test
    void validStudent_passes() {
        var v = XsdValidator.fromClasspath("/schema/formatStudent.xsd");
        assertTrue(v.validate(
            "<students><student>" +
            "<id>S1</id><name>张三</name><sex>男</sex><major>CS</major><origin>A</origin>" +
            "</student></students>").valid());
    }

    @Test
    void choiceWithBadOrigin_fails() {
        var v = XsdValidator.fromClasspath("/schema/formatChoice.xsd");
        assertFalse(v.validate(
            "<choices><choice><sid>S1</sid><cid>C1</cid>" +
            "<originStudent>A</originStudent><originCourse>Z</originCourse>" +
            "</choice></choices>").valid());
    }
}
