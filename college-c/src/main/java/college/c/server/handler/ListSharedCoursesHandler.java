package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import cn.edu.di.xml.XsdValidator;
import cn.edu.di.xml.XsltTransformer;
import college.c.dao.CourseDao;
import college.c.xml.CourseCAdapter;

import java.net.Socket;
import java.util.UUID;

public class ListSharedCoursesHandler implements Handler {

  private final String integrationHost;
  private final int integrationPort;
  private final String fromCollege;
  private final String toLocalXsl;
  private final CourseDao courseDao;

  public ListSharedCoursesHandler(String integrationHost, int integrationPort,
                                  String fromCollege, String toLocalXsl,
                                  CourseDao courseDao) {
    this.integrationHost = integrationHost;
    this.integrationPort = integrationPort;
    this.fromCollege = fromCollege;
    this.toLocalXsl = toLocalXsl;
    this.courseDao = courseDao;
  }

  @Override
  public Message handle(Message req) {
    try (var sock = new Socket(integrationHost, integrationPort)) {
      Message fetchReq = new Message(Command.FETCH_SHARED_COURSES,
          UUID.randomUUID().toString(), "<from>" + fromCollege + "</from>");
      Message.write(sock.getOutputStream(), fetchReq);
      Message fetchResp = Message.read(sock.getInputStream());

      if (fetchResp.command() != Command.OK) return fetchResp;

      String unifiedXml = fetchResp.payload();
      var result = XsdValidator.fromClasspath("/schema/formatClass.xsd").validate(unifiedXml);
      if (!result.valid()) {
        return Message.err(req.requestId(), "XML_SCHEMA", result.errors().toString());
      }
      String localXml = XsltTransformer.fromClasspath(toLocalXsl).transform(unifiedXml);
      int saved = courseDao.upsertShared(CourseCAdapter.unmarshal(XmlIO.parse(localXml)));
      System.out.println("saved shared courses for college C: " + saved);
      return Message.ok(req.requestId(), localXml);
    } catch (Exception e) {
      return Message.err(req.requestId(), "INTEGRATION_FAILED", e.getMessage());
    }
  }
}
