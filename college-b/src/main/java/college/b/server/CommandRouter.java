package college.b.server;

import college.b.server.handler.Handler;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import java.util.EnumMap;
import java.util.Map;

public class CommandRouter {
  private final Map<Command, Handler> handlers = new EnumMap<>(Command.class);

  public CommandRouter register(Command cmd, Handler h) {
    handlers.put(cmd, h);
    return this;
  }

  public Message dispatch(Message req) {
    Handler h = handlers.get(req.command());
    if (h == null) return Message.err(req.requestId(), "UNKNOWN_CMD", "no handler for " + req.command());
    return h.handle(req);
  }
}
