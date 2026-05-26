package cn.edu.di.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MessageFrameTest {

    @Test
    void encode_thenDecode_roundTrip() throws Exception {
        MessageFrame original = new MessageFrame("ENROLL", "req-001",
                "<enroll><sid>S1</sid><cid>C1</cid></enroll>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.writeTo(out);
        MessageFrame decoded = MessageFrame.readFrom(new ByteArrayInputStream(out.toByteArray()));
        assertEquals("ENROLL", decoded.command());
        assertEquals("req-001", decoded.requestId());
        assertEquals(original.payload(), decoded.payload());
    }

    @Test
    void emptyPayload_encodesContentLengthZero() throws Exception {
        MessageFrame f = new MessageFrame("PING", "r-1", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        f.writeTo(out);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Content-Length: 0"));
    }

    @Test
    void utf8PayloadLengthMeasuredInBytes() throws Exception {
        MessageFrame f = new MessageFrame("X", "r", "中");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        f.writeTo(out);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Content-Length: 3"));
    }

    @Test
    void malformedHeader_throwsProtocolException() {
        ByteArrayInputStream in = new ByteArrayInputStream("BADLINE\n\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(ProtocolException.class, () -> MessageFrame.readFrom(in));
    }

    @Test
    void contentLengthMismatch_throwsProtocolException() {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "X r\nContent-Length: 100\n\nshort".getBytes(StandardCharsets.UTF_8));
        assertThrows(ProtocolException.class, () -> MessageFrame.readFrom(in));
    }
}
