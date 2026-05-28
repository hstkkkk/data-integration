package analytics.monitor;

import analytics.model.MonitorSnapshot;
import analytics.model.ServerStatus;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class MonitorPoller {

  private final String integrationHost;
  private final int integrationPort;
  private final Consumer<MonitorSnapshot> onUpdate;
  private Timer timer;

  public MonitorPoller(String integrationHost, int integrationPort, Consumer<MonitorSnapshot> onUpdate) {
    this.integrationHost = integrationHost;
    this.integrationPort = integrationPort;
    this.onUpdate = onUpdate;
  }

  public void start(int intervalMs) {
    timer = new Timer(intervalMs, e -> poll());
    timer.setInitialDelay(0);
    timer.start();
  }

  public void stop() {
    if (timer != null) timer.stop();
  }

  private void poll() {
    try (var sock = new Socket(integrationHost, integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.MONITOR_STATUS, UUID.randomUUID().toString(), ""));
      Message resp = Message.read(sock.getInputStream());
      if (resp.command() == Command.OK) {
        var doc = XmlIO.parse(resp.payload());
        List<ServerStatus> servers = new ArrayList<>();
        for (var obj : doc.getRootElement().elements("server")) {
          var el = (org.dom4j.Element) obj;
          servers.add(new ServerStatus(
              el.attributeValue("name"),
              Boolean.parseBoolean(el.attributeValue("online")),
              Long.parseLong(el.attributeValue("latencyMs")),
              Integer.parseInt(el.attributeValue("requestCount")),
              Integer.parseInt(el.attributeValue("errorCount")),
              Long.parseLong(el.attributeValue("uptimeSeconds"))));
        }
        onUpdate.accept(new MonitorSnapshot(servers));
      }
    } catch (IOException e) {
      // silently skip this cycle if integration server is unreachable
    }
  }
}
