package cn.edu.di.xml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XsltTransformerTest {

    @Test
    void identityXsl_returnsSameStructure() {
        String out = XsltTransformer.fromClasspath("/test-xsl/identity.xsl")
                                    .transform("<a><b>x</b></a>");
        assertTrue(out.contains("<a>"));
        assertTrue(out.contains("<b>x</b>"));
    }

    @Test
    void appliesActualTransform() {
        String out = XsltTransformer.fromClasspath("/test-xsl/uppercase-name.xsl")
                                    .transform("<root><name>alice</name><name>bob</name></root>");
        assertTrue(out.contains("<name>ALICE</name>"));
        assertTrue(out.contains("<name>BOB</name>"));
    }
}
