package college.b.server.handler;

import cn.edu.di.protocol.Message;

public class HeartbeatHandler implements Handler {
  private final String collegeCode;
  private final long startTime = System.currentTimeMillis();
  private int requestCount = 0;

  public HeartbeatHandler(String collegeCode) { this.collegeCode = collegeCode; }

  @Override
  public Message handle(Message request) {
    requestCount++;
    long uptime = (System.currentTimeMillis() - startTime) / 1000;
    return Message.ok(request.requestId(),
        "<heartbeat college=\"" + collegeCode + "\" online=\"true\" latencyMs=\"0\""
        + " requestCount=\"" + requestCount + "\" errorCount=\"0\""
        + " uptimeSeconds=\"" + uptime + "\"/>");
  }
}
