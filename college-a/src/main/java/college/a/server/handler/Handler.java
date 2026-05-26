package college.a.server.handler;

import cn.edu.di.protocol.Message;

public interface Handler {
  Message handle(Message request);
}
