package cn.edu.di.xml;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class XsltTransformer {

    private final byte[] xslBytes;
    private XsltTransformer(byte[] b) { this.xslBytes = b; }

    public static XsltTransformer fromClasspath(String path) {
        try (InputStream in = XsltTransformer.class.getResourceAsStream(path)) {
            if (in == null) throw new XmlException("xsl not found: " + path);
            return new XsltTransformer(in.readAllBytes());
        } catch (Exception e) { throw new XmlException("load xsl: " + path, e); }
    }

    public String transform(String xml) {
        try {
            Transformer t = TransformerFactory.newInstance()
                    .newTransformer(new StreamSource(new ByteArrayInputStream(xslBytes)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transform(
                    new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))),
                    new StreamResult(out));
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) { throw new XmlException("transform: " + e.getMessage(), e); }
    }
}
