package cn.edu.di.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XsdValidatorTest {
    private final XsdValidator v = XsdValidator.fromClasspath("/test-schemas/sample.xsd");

    @Test
    void validXml_returnsValid() {
        var r = v.validate("<person><name>X</name><age>20</age></person>");
        assertTrue(r.valid(), r.toString());
    }

    @Test
    void wrongElement_returnsInvalid() {
        var r = v.validate("<animal><kind>cat</kind></animal>");
        assertFalse(r.valid());
        assertFalse(r.errors().isEmpty());
    }

    @Test
    void negativeAge_invalid() {
        var r = v.validate("<person><name>X</name><age>-5</age></person>");
        assertFalse(r.valid());
    }
}
