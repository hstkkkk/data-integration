package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlException;
import cn.edu.di.xml.XmlIO;
import college.a.service.AuthService;
import org.dom4j.Document;
import org.dom4j.Element;

public class LoginHandler implements Handler {

  private final AuthService authService;

  public LoginHandler(AuthService authService) {
    this.authService = authService;
  }

  @Override
  public Message handle(Message request) {
    try {
      Document doc = XmlIO.parse(request.payload());
      Element root = doc.getRootElement();
      String user = root.elementText("user");
      String pass = root.elementText("pass");

      return authService.login(user, pass)
          .map(session -> Message.ok(request.requestId(),
              "<session><role>" + esc(session.role()) + "</role></session>"))
          .orElseGet(() -> Message.err(request.requestId(), "AUTH_FAILED",
              "invalid credentials"));
    } catch (XmlException e) {
      return Message.err(request.requestId(), "BAD_PAYLOAD",
          "invalid XML: " + e.getMessage());
    }
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
