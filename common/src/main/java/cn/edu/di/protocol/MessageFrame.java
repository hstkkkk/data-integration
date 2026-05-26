package cn.edu.di.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;

public record MessageFrame(String command, String requestId, String payload) {

    public void writeTo(OutputStream out) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        StringBuilder hdr = new StringBuilder()
                .append(command).append(' ').append(requestId).append('\n')
                .append("Content-Length: ").append(body.length).append('\n')
                .append('\n');
        out.write(hdr.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    public static MessageFrame readFrom(InputStream in) throws IOException {
        String firstLine = readLine(in);
        if (firstLine == null) throw new ProtocolException("EOF before header");
        String[] parts = firstLine.split(" ", 2);
        if (parts.length != 2) throw new ProtocolException("Bad header: " + firstLine);

        String clLine = readLine(in);
        if (clLine == null || !clLine.startsWith("Content-Length: "))
            throw new ProtocolException("Missing Content-Length");
        int len;
        try { len = Integer.parseInt(clLine.substring("Content-Length: ".length()).trim()); }
        catch (NumberFormatException e) { throw new ProtocolException("Bad Content-Length", e); }

        String blank = readLine(in);
        if (blank == null || !blank.isEmpty()) throw new ProtocolException("Missing blank line");

        byte[] body = in.readNBytes(len);
        if (body.length != len) throw new ProtocolException(
                "Truncated body: expected " + len + " got " + body.length);
        return new MessageFrame(parts[0], parts[1], new String(body, StandardCharsets.UTF_8));
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') return buf.toString(StandardCharsets.UTF_8);
            buf.write(b);
        }
        return buf.size() == 0 ? null : buf.toString(StandardCharsets.UTF_8);
    }
}
