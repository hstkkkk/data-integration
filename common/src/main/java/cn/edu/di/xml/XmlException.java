package cn.edu.di.xml;

public class XmlException extends RuntimeException {
    public XmlException(String m, Throwable c) { super(m, c); }
    public XmlException(String m) { super(m); }
}
