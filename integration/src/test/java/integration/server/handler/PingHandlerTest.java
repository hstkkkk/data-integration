package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PingHandlerTest {

  @Test
  void ping_returns_ok_with_pong() {
    var handler = new PingHandler();
    var req = new Message(Command.PING, "r1", "");
    var res = handler.handle(req);

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("pong"));
  }
}
