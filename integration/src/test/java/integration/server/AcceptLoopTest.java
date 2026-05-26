package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.server.handler.PingHandler;
import org.junit.jupiter.api.Test;

import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class AcceptLoopTest {

  @Test
  void ping_roundtrip() throws Exception {
    IntegrationRouter router = new IntegrationRouter()
        .register(Command.PING, new PingHandler());
    IntegrationServer server = new IntegrationServer(0, router);
    Thread serverThread = new Thread(() -> {
      try { server.serve(); } catch (Exception ignored) { /* shutdown */ }
    });
    serverThread.start();

    // Give the server a moment to bind and start accepting.
    Thread.sleep(200);

    try {
      try (Socket sock = new Socket("localhost", server.getPort())) {
        Message req = new Message(Command.PING, "r1", "");
        Message.write(sock.getOutputStream(), req);
        Message res = Message.read(sock.getInputStream());
        assertEquals(Command.OK, res.command());
        assertTrue(res.payload().contains("pong"));
      }
    } finally {
      server.close();
      serverThread.join(2000);
    }
  }
}
