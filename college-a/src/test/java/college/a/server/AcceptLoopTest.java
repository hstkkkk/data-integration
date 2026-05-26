package college.a.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;

import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class AcceptLoopTest {

  @Test
  void emptyRouter_returnsUnknownCmd() throws Exception {
    CommandRouter router = new CommandRouter();
    CollegeAServer server = new CollegeAServer(0, router);
    Thread serverThread = new Thread(() -> {
      try { server.serve(); } catch (Exception ignored) { /* shutdown */ }
    });
    serverThread.start();

    // Give the server a moment to bind and start accepting.
    Thread.sleep(200);

    try {
      try (Socket sock = new Socket("localhost", server.getPort())) {
        Message req = new Message(Command.LOGIN, "r1", "<login/>");
        Message.write(sock.getOutputStream(), req);
        Message res = Message.read(sock.getInputStream());
        assertEquals(Command.ERR, res.command());
        assertTrue(res.payload().contains("UNKNOWN_CMD"));
      }
    } finally {
      server.close();
      serverThread.join(2000);
    }
  }
}
