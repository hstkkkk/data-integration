package cn.edu.di.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public record Message(Command command, String requestId, String payload) {

    public static Message fromFrame(MessageFrame f) {
        return new Message(Command.parse(f.command()), f.requestId(), f.payload());
    }

    public MessageFrame toFrame() {
        return new MessageFrame(command.name(), requestId, payload);
    }

    /** Read a Message off the wire (decode frame + parse command). */
    public static Message read(InputStream in) throws IOException {
        return fromFrame(MessageFrame.readFrom(in));
    }

    /** Write a Message to the wire (encode frame). */
    public static void write(OutputStream out, Message m) throws IOException {
        m.toFrame().writeTo(out);
    }

    public static Message ok(String reqId, String payload) {
        return new Message(Command.OK, reqId, payload == null ? "" : payload);
    }

    public static Message err(String reqId, String code, String detail) {
        String xml = "<error><code>" + esc(code) + "</code><detail>" + esc(detail) + "</detail></error>";
        return new Message(Command.ERR, reqId, xml);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
