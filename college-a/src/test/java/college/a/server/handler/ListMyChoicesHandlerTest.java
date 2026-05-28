package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.server.CollegeServerConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListMyChoicesHandlerTest {

  private static final String CROSS_RESULT_OK =
      "<crossEnrolledResult>"
      + "<classes><class origin=\"B\">"
      + "<id>BC003</id><name>OS</name><time>32</time><score>3</score>"
      + "<teacher>Wang</teacher><location>B201</location><share>Y</share>"
      + "<sno>AS001</sno><grade></grade></class></classes>"
      + "<errors/></crossEnrolledResult>";

  private ChoiceDao choiceDaoWith(String courseId) {
    var d = mock(ChoiceDao.class);
    when(d.findByStudent("AS001")).thenReturn(List.of(
        new ChoiceDao.Row(courseId, "AS001", null, "A")));
    return d;
  }

  private CourseDao courseDaoWith(String courseId, String name) {
    var d = mock(CourseDao.class);
    when(d.findById(courseId)).thenReturn(Optional.of(
        new CourseDao.Row(courseId, name, 48, BigDecimal.valueOf(3),
            "Zhao", "A101", false)));
    return d;
  }

  /** integration mock: accept one connection, reply with given Message. */
  private ServerSocket startMockIntegration(Message reply) throws Exception {
    var ss = new ServerSocket(0);
    new Thread(() -> {
      try (Socket sock = ss.accept()) {
        Message.read(sock.getInputStream()); // consume request
        Message.write(sock.getOutputStream(), reply);
      } catch (Exception ignored) {}
    }).start();
    return ss;
  }

  @Test
  void wraps_local_and_cross_choices_into_myChoices() throws Exception {
    var choiceDao = choiceDaoWith("AC001");
    var courseDao = courseDaoWith("AC001", "Math");
    try (var integ = startMockIntegration(Message.ok("r", CROSS_RESULT_OK))) {
      System.setProperty("integration.port", Integer.toString(integ.getLocalPort()));
      var config = new CollegeServerConfig("A", "AC", "AS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);

      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>AS001</sno>"));

      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<myChoices sno=\"AS001\" home=\"A\">"));
      assertTrue(res.payload().contains("<home>"));
      assertTrue(res.payload().contains("<课程编号>AC001</课程编号>"));
      assertTrue(res.payload().contains("<crossEnrolled>"));
      assertTrue(res.payload().contains("<来源>B</来源>"));
      assertTrue(res.payload().contains("<课程编号>BC003</课程编号>"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void integration_unreachable_keeps_home_block_and_records_error() {
    var choiceDao = choiceDaoWith("AC001");
    var courseDao = courseDaoWith("AC001", "Math");
    System.setProperty("integration.port", "1");  // surely refused
    try {
      var config = new CollegeServerConfig("A", "AC", "AS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);

      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>AS001</sno>"));

      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<课程编号>AC001</课程编号>"));
      assertTrue(res.payload().contains("<error college=\"*\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void local_dao_failure_returns_err() {
    var choiceDao = mock(ChoiceDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db"));
    var courseDao = mock(CourseDao.class);
    var config = new CollegeServerConfig("A", "AC", "AS");
    var h = new ListMyChoicesHandler(choiceDao, courseDao, config);

    var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>AS001</sno>"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }

  @Test
  void bad_payload_returns_err() {
    var h = new ListMyChoicesHandler(mock(ChoiceDao.class), mock(CourseDao.class),
        new CollegeServerConfig("A", "AC", "AS"));
    var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "not xml"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("BAD_PAYLOAD"));
  }
}
