package cn.edu.di.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void parseFromFrame_setsCommandEnum() {
        Message m = Message.fromFrame(new MessageFrame("ENROLL", "r-1", "<x/>"));
        assertEquals(Command.ENROLL, m.command());
        assertEquals("r-1", m.requestId());
        assertEquals("<x/>", m.payload());
    }

    @Test
    void unknownCommand_yieldsCommandUnknown() {
        Message m = Message.fromFrame(new MessageFrame("FOOBAR", "r", ""));
        assertEquals(Command.UNKNOWN, m.command());
    }

    @Test
    void okResponse_factory() {
        Message m = Message.ok("r-9", "<result/>");
        assertEquals(Command.OK, m.command());
        assertEquals("<result/>", m.payload());
    }

    @Test
    void errResponse_carriesCodeAndDetail() {
        Message m = Message.err("r-9", "BUSINESS", "course full");
        assertEquals(Command.ERR, m.command());
        assertTrue(m.payload().contains("BUSINESS"));
        assertTrue(m.payload().contains("course full"));
    }

    @Test
    void readWrite_roundTrip() throws Exception {
        Message original = new Message(Command.ENROLL, "r-7", "<enroll/>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Message.write(out, original);
        Message decoded = Message.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(original, decoded);
    }
}
