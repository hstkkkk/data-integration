package college.c.server.handler;

import cn.edu.di.protocol.Message;

public interface Handler {
  Message handle(Message request);
}
