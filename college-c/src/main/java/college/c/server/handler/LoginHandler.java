package college.c.server.handler;

import college.c.service.AuthService;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

public class LoginHandler implements Handler {
  private final AuthService auth;
  public LoginHandler(AuthService auth) { this.auth = auth; }

  @Override
  public Message handle(Message req) {
    try {
      var doc = XmlIO.parse(req.payload());
      String user = doc.getRootElement().elementText("user");
      String pass = doc.getRootElement().elementText("pass");
      return auth.login(user, pass)
          .map(s -> Message.ok(req.requestId(),
              "<session><user>" + s.username() + "</user><role>" + s.role() + "</role></session>"))
          .orElseGet(() -> Message.err(req.requestId(), "AUTH_FAILED", "username or password incorrect"));
    } catch (Exception e) {
      return Message.err(req.requestId(), "BAD_PAYLOAD", e.getMessage());
    }
  }
}
