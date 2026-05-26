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
        try { return new SAXReader().read(new StringReader(xml)); }
        catch (Exception e) { throw new XmlException("parse failed: " + e.getMessage(), e); }
    }

    public static Document parse(InputStream in) {
        try { return new SAXReader().read(new InputStreamReader(in, StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new XmlException("parse failed: " + e.getMessage(), e); }
    }

    public static String toPrettyString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            OutputFormat fmt = OutputFormat.createPrettyPrint();
            fmt.setEncoding("UTF-8");
            XMLWriter xw = new XMLWriter(sw, fmt);
            xw.write(doc);
            xw.flush();
            return sw.toString();
        } catch (IOException e) { throw new XmlException("write failed", e); }
    }
}
