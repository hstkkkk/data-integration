package cn.edu.di.xml;

import org.dom4j.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XmlIOTest {

    @Test
    void readsValidXmlIntoDocument() {
        Document doc = XmlIO.parse("<root><child id=\"1\"/></root>");
        assertEquals("root", doc.getRootElement().getName());
        assertEquals("1", doc.getRootElement().element("child").attributeValue("id"));
    }

    @Test
    void writesDocumentToString_pretty() {
        String out = XmlIO.toPrettyString(XmlIO.parse("<a><b>hi</b></a>"));
        assertTrue(out.contains("<a>"));
        assertTrue(out.contains("<b>hi</b>"));
        assertTrue(out.startsWith("<?xml"));
    }

    @Test
    void invalidXml_throwsXmlException() {
        assertThrows(XmlException.class, () -> XmlIO.parse("<broken"));
    }

    @Test
    void utf8RoundTrip() {
        String s = XmlIO.toPrettyString(XmlIO.parse("<n>张三</n>"));
        assertTrue(s.contains("张三"));
    }
}
