package college.a.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.server.handler.Handler;

import java.util.EnumMap;
import java.util.Map;

public class CommandRouter {

  private final Map<Command, Handler> handlers = new EnumMap<>(Command.class);

  public CommandRouter register(Command cmd, Handler handler) {
    handlers.put(cmd, handler);
    return this;
  }

  public Message dispatch(Message request) {
    Handler h = handlers.get(request.command());
    if (h == null) {
      return Message.err(request.requestId(), "UNKNOWN_CMD",
          "no handler for " + request.command());
    }
    try {
      return h.handle(request);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Message.err(request.requestId(), "SERVER_ERROR", e.getMessage());
    }
  }
}
