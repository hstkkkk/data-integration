package cn.edu.di.xml;

import org.dom4j.Document;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class XmlIO {

    private XmlIO() {}

    public static Document parse(String xml) {
        try { return createSafeReader().read(new StringReader(xml)); }
        catch (Exception e) { throw new XmlException("parse failed: " + e.getMessage(), e); }
    }

    public static Document parse(InputStream in) {
        try { return createSafeReader().read(new InputStreamReader(in, StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new XmlException("parse failed: " + e.getMessage(), e); }
    }

    private static SAXReader createSafeReader() {
        SAXReader reader = new SAXReader();
        try {
            reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignore) {}
        return reader;
    }

    public static String toPrettyString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            OutputFormat fmt = OutputFormat.createPrettyPrint();
            fmt.setEncoding("UTF-8");
            fmt.setExpandEmptyElements(true);
            XMLWriter xw = new XMLWriter(sw, fmt);
            xw.write(doc);
            xw.flush();
            return sw.toString();
        } catch (IOException e) { throw new XmlException("write failed", e); }
    }
}
