package cn.edu.di.protocol;

public class ProtocolException extends RuntimeException {
    public ProtocolException(String msg) { super(msg); }
    public ProtocolException(String msg, Throwable cause) { super(msg, cause); }
}
