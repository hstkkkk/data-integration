package college.c.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;
import java.net.Socket;
import static org.junit.jupiter.api.Assertions.*;

class AcceptLoopTest {
  @Test void unknown_command_returns_err() throws Exception {
    var router = new CommandRouter();
    try (var srv = new CollegeCServer(0, router)) {
      Thread t = new Thread(srv::serve);
      t.start();
      try (var sock = new Socket("127.0.0.1", srv.getPort())) {
        Message.write(sock.getOutputStream(), new Message(Command.LOGIN, "rx", "<x/>"));
        Message res = Message.read(sock.getInputStream());
        assertEquals(Command.ERR, res.command());
        assertTrue(res.payload().contains("UNKNOWN_CMD"));
      }
      srv.close();
      t.join(1000);
    }
  }
}
