package integration.net;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import static org.junit.jupiter.api.Assertions.*;

class CollegeClientTest {
  @Test
  void send_returns_server_response() throws Exception {
    try (var server = new ServerSocket(0)) {
      new Thread(() -> {
        try (var s = server.accept()) {
          Message req = Message.read(s.getInputStream());
          Message.write(s.getOutputStream(), Message.ok(req.requestId(), "<echo/>"));
        } catch (Exception ignore) {}
      }).start();

      var client = new CollegeClient("127.0.0.1", server.getLocalPort());
      Message res = client.send(new Message(Command.ASK_COURSE_INFO, "r1", ""));
      assertEquals(Command.OK, res.command());
    }
  }
}
