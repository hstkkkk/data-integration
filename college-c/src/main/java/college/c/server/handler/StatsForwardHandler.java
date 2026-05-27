package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.c.server.CollegeServerConfig;

import java.net.Socket;
import java.util.UUID;

public class StatsForwardHandler implements Handler {
  private final CollegeServerConfig config;

  public StatsForwardHandler(CollegeServerConfig config) {
    this.config = config;
  }

  @Override
  public Message handle(Message req) {
    try (var sock = new Socket(config.integrationHost, config.integrationPort)) {
      Message.write(sock.getOutputStream(),
          new Message(Command.STATS_GLOBAL, UUID.randomUUID().toString(), ""));
      Message resp = Message.read(sock.getInputStream());
      return resp.command() == Command.OK
          ? Message.ok(req.requestId(), resp.payload())
          : Message.err(req.requestId(), "STATS_FAILED", resp.payload());
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
