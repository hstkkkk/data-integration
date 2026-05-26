package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.server.handler.Handler;

import java.util.EnumMap;
import java.util.Map;

public class IntegrationRouter {

  private final Map<Command, Handler> handlers = new EnumMap<>(Command.class);

  public IntegrationRouter register(Command cmd, Handler handler) {
    handlers.put(cmd, handler);
    return this;
  }

  public Message dispatch(Message request) {
    Handler h = handlers.get(request.command());
    if (h == null) {
      return Message.err(request.requestId(), "UNKNOWN_CMD",
          "no handler for " + request.command());
    }
    return h.handle(request);
  }
}
