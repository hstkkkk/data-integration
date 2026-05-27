package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import integration.server.handler.CrossEnrollHandler;
import integration.server.handler.CrossWithdrawHandler;
import integration.server.handler.StatsGlobalHandler;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossCollegeFlowsTest {

  @Test
  void crossEnroll_routes_by_course_prefix() throws Exception {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    when(clientB.send(any())).thenReturn(Message.ok("rid", ""));

    var handler = new CrossEnrollHandler(clientA, clientB, clientC);
    String payload = "<crossEnroll><courseId>BC001</courseId>"
        + "<studentId>AS001</studentId><fromCollege>A</fromCollege></crossEnroll>";
    Message res = handler.handle(new Message(Command.CROSS_ENROLL,
        UUID.randomUUID().toString(), payload));

    assertEquals(Command.OK, res.command());
    verify(clientB).send(any());
    verify(clientA, never()).send(any());
    verify(clientC, never()).send(any());
  }

  @Test
  void crossEnroll_unknown_prefix_returns_err() {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);

    var handler = new CrossEnrollHandler(clientA, clientB, clientC);
    String payload = "<crossEnroll><courseId>XX999</courseId>"
        + "<studentId>AS001</studentId><fromCollege>A</fromCollege></crossEnroll>";
    Message res = handler.handle(new Message(Command.CROSS_ENROLL,
        UUID.randomUUID().toString(), payload));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("UNKNOWN_COURSE"),
        "expected UNKNOWN_COURSE in payload, got: " + res.payload());
  }

  @Test
  void crossWithdraw_routes_by_course_prefix() throws Exception {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    when(clientC.send(any())).thenReturn(Message.ok("rid", ""));

    var handler = new CrossWithdrawHandler(clientA, clientB, clientC);
    String payload = "<crossWithdraw><courseId>CC005</courseId>"
        + "<studentId>BS010</studentId><fromCollege>B</fromCollege></crossWithdraw>";
    Message res = handler.handle(new Message(Command.CROSS_WITHDRAW,
        UUID.randomUUID().toString(), payload));

    assertEquals(Command.OK, res.command());
    verify(clientC).send(any());
    verify(clientA, never()).send(any());
    verify(clientB, never()).send(any());
  }

  @Test
  void statsGlobal_aggregates_three_colleges() throws Exception {
    var clientA = mock(CollegeClient.class);
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    when(clientA.send(any())).thenReturn(Message.ok("rid",
        pullReply("A", 50, 10, 3, 5,
            "AC001", "课程一", 30, "AC002", "课程二", 20)));
    when(clientB.send(any())).thenReturn(Message.ok("rid",
        pullReply("B", 50, 10, 4, 7,
            "BC001", "课程三", 28, "BC002", "课程四", 18)));
    when(clientC.send(any())).thenReturn(Message.ok("rid",
        pullReply("C", 50, 10, 2, 0,
            "CC001", "课程五", 25, "CC002", "课程六", 15)));

    var handler = new StatsGlobalHandler(clientA, clientB, clientC);
    Message res = handler.handle(new Message(Command.STATS_GLOBAL,
        UUID.randomUUID().toString(), ""));

    assertEquals(Command.OK, res.command());
    String xml = res.payload();
    assertTrue(xml.contains("<totalStudents>150</totalStudents>"), xml);
    assertTrue(xml.contains("<totalCourses>30</totalCourses>"), xml);
    assertTrue(xml.contains("<totalSharedCourses>9</totalSharedCourses>"), xml);
    assertTrue(xml.contains("<crossEnrollments>12</crossEnrollments>"), xml);

    int topIdx = xml.indexOf("<topCourses>");
    assertTrue(topIdx > 0, "topCourses section missing");
    String topSection = xml.substring(topIdx);
    int idxAC001 = topSection.indexOf("id=\"AC001\"");
    int idxBC001 = topSection.indexOf("id=\"BC001\"");
    assertTrue(idxAC001 > 0 && idxBC001 > 0, "Top entries missing: " + topSection);
    assertTrue(idxAC001 < idxBC001,
        "AC001(30) should rank before BC001(28) in: " + topSection);
  }

  private static String pullReply(String code, int students, int courses,
                                  int shared, int cross,
                                  String c1Id, String c1Name, int c1Enr,
                                  String c2Id, String c2Name, int c2Enr) {
    return "<pullData college=\"" + code + "\">"
        + "<studentCount>" + students + "</studentCount>"
        + "<courseCount>" + courses + "</courseCount>"
        + "<sharedCount>" + shared + "</sharedCount>"
        + "<crossEnrollmentCount>" + cross + "</crossEnrollmentCount>"
        + "<courses>"
        + "<course id=\"" + c1Id + "\" name=\"" + c1Name
        + "\" enrollments=\"" + c1Enr + "\"/>"
        + "<course id=\"" + c2Id + "\" name=\"" + c2Name
        + "\" enrollments=\"" + c2Enr + "\"/>"
        + "</courses>"
        + "</pullData>";
  }
}
