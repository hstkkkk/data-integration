package cn.edu.di.xml;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XsdValidator {

    private final Schema schema;
    private XsdValidator(Schema s) { this.schema = s; }

    public static XsdValidator fromClasspath(String path) {
        try (InputStream in = XsdValidator.class.getResourceAsStream(path)) {
            if (in == null) throw new XmlException("xsd not found: " + path);
            byte[] bytes = in.readAllBytes();
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return new XsdValidator(sf.newSchema(new StreamSource(new ByteArrayInputStream(bytes))));
        } catch (Exception e) { throw new XmlException("load xsd: " + path, e); }
    }

    public Result validate(String xml) {
        Validator v = schema.newValidator();
        List<String> errs = new ArrayList<>();
        v.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) { errs.add("WARN " + e.getMessage()); }
            public void error(SAXParseException e) { errs.add("ERROR " + e.getMessage()); }
            public void fatalError(SAXParseException e) { errs.add("FATAL " + e.getMessage()); }
        });
        try {
            v.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) { errs.add("EXCEPTION " + e.getMessage()); }
        return new Result(errs.isEmpty(), Collections.unmodifiableList(errs));
    }

    public record Result(boolean valid, List<String> errors) {
        @Override public String toString() {
            return valid ? "valid" : "invalid: " + String.join("; ", errors);
        }
    }
}
