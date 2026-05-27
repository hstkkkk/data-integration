package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import integration.net.CollegeClient;

import java.util.UUID;

public class CrossWithdrawHandler implements Handler {

  private final CollegeClient clientA;
  private final CollegeClient clientB;
  private final CollegeClient clientC;

  public CrossWithdrawHandler(CollegeClient clientA, CollegeClient clientB, CollegeClient clientC) {
    this.clientA = clientA;
    this.clientB = clientB;
    this.clientC = clientC;
  }

  @Override
  public Message handle(Message req) {
    try {
      var doc = XmlIO.parse(req.payload());
      String courseId = doc.getRootElement().elementText("courseId");

      CollegeClient target = targetFor(courseId);
      if (target == null) return Message.err(req.requestId(), "UNKNOWN_COURSE", courseId);

      Message revoke = target.send(new Message(Command.REVOKE_CHOICE,
          UUID.randomUUID().toString(), req.payload()));
      return revoke.command() == Command.OK
          ? Message.ok(req.requestId(), revoke.payload())
          : Message.err(req.requestId(), "TARGET_REJECTED", revoke.payload());
    } catch (Exception e) {
      return Message.err(req.requestId(), "CROSS_FAILED", e.getMessage());
    }
  }

  private CollegeClient targetFor(String courseId) {
    if (courseId == null) return null;
    if (courseId.startsWith("AC")) return clientA;
    if (courseId.startsWith("BC")) return clientB;
    if (courseId.startsWith("CC")) return clientC;
    return null;
  }
}
