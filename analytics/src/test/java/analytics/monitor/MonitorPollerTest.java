package analytics.monitor;

import analytics.model.MonitorSnapshot;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class MonitorPollerTest {
  @Test void poll_receives_server_statuses() throws Exception {
    try (var server = new ServerSocket(0)) {
      var latch = new CountDownLatch(1);
      var ref = new Object() { MonitorSnapshot snapshot; };

      var poller = new MonitorPoller("127.0.0.1", server.getLocalPort(), snap -> {
        ref.snapshot = snap;
        latch.countDown();
      });

      // Simulate Integration Server response
      new Thread(() -> {
        try (var sock = server.accept()) {
          Message req = Message.read(sock.getInputStream());
          Message.write(sock.getOutputStream(), Message.ok(req.requestId(),
              "<monitor timestamp=\"2026-05-28T10:00:00Z\">" +
              "<server name=\"I\" online=\"true\" latencyMs=\"12\" requestCount=\"250\" errorCount=\"0\" uptimeSeconds=\"3600\"/>" +
              "<server name=\"A\" online=\"true\" latencyMs=\"15\" requestCount=\"180\" errorCount=\"2\" uptimeSeconds=\"3500\"/>" +
              "<server name=\"B\" online=\"false\" latencyMs=\"0\" requestCount=\"0\" errorCount=\"0\" uptimeSeconds=\"0\"/>" +
              "<server name=\"C\" online=\"true\" latencyMs=\"8\" requestCount=\"200\" errorCount=\"1\" uptimeSeconds=\"3400\"/>" +
              "</monitor>"));
        } catch (Exception ignore) {}
      }).start();

      poller.start(100);
      assertTrue(latch.await(3, TimeUnit.SECONDS));
      assertNotNull(ref.snapshot);
      assertEquals(4, ref.snapshot.servers().size());
      assertTrue(ref.snapshot.servers().get(0).online());
      assertFalse(ref.snapshot.servers().get(2).online()); // B is offline
      poller.stop();
    }
  }
}
