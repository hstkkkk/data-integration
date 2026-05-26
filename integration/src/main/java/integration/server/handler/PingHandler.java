package integration.server.handler;

import cn.edu.di.protocol.Message;

public class PingHandler implements Handler {

  @Override
  public Message handle(Message request) {
    return Message.ok(request.requestId(), "<pong>integration-server</pong>");
  }
}
