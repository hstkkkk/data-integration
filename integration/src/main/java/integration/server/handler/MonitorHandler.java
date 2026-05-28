package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import java.util.UUID;

public class MonitorHandler implements Handler {

  private final CollegeClient clientA, clientB, clientC;
  private final long startTime = System.currentTimeMillis();
  private int requestCount = 0;

  public MonitorHandler(CollegeClient ca, CollegeClient cb, CollegeClient cc) {
    this.clientA = ca; this.clientB = cb; this.clientC = cc;
  }

  @Override
  public Message handle(Message req) {
    requestCount++;
    long uptime = (System.currentTimeMillis() - startTime) / 1000;

    StringBuilder xml = new StringBuilder(
        "<monitor timestamp=\"" + java.time.Instant.now() + "\">");

    // Self check
    xml.append("<server name=\"I\" online=\"true\" latencyMs=\"0\" requestCount=\"")
       .append(requestCount).append("\" errorCount=\"0\" uptimeSeconds=\"")
       .append(uptime).append("\"/>");

    // Check each college
    checkCollege(xml, clientA, "A");
    checkCollege(xml, clientB, "B");
    checkCollege(xml, clientC, "C");

    xml.append("</monitor>");
    return Message.ok(req.requestId(), xml.toString());
  }

  private void checkCollege(StringBuilder xml, CollegeClient client, String name) {
    try {
      long start = System.currentTimeMillis();
      Message resp = client.send(new Message(Command.HEARTBEAT, UUID.randomUUID().toString(), ""));
      long latency = System.currentTimeMillis() - start;
      if (resp.command() == Command.OK) {
        // Reuse heartbeat response but replace latencyMs
        String hb = resp.payload();
        hb = hb.replace("latencyMs=\"0\"", "latencyMs=\"" + latency + "\"");
        xml.append(hb);
        return;
      }
    } catch (Exception ignore) {
      // fall through to offline entry
    }
    xml.append("<server name=\"").append(name)
        .append("\" online=\"false\" latencyMs=\"0\" requestCount=\"0\"")
        .append(" errorCount=\"0\" uptimeSeconds=\"0\"/>");
  }
}
