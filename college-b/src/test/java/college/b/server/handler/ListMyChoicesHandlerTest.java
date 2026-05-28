package college.b.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import college.b.server.CollegeServerConfig;
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
      + "<classes><class origin=\"A\">"
      + "<id>AC001</id><name>Math</name><time>48</time><score>3</score>"
      + "<teacher>Zhao</teacher><location>A101</location><share>Y</share>"
      + "<sno>BS001</sno><grade></grade></class></classes>"
      + "<errors/></crossEnrolledResult>";

  private ChoiceDao choiceDaoWith(String courseId) {
    var d = mock(ChoiceDao.class);
    when(d.findByStudent("BS001")).thenReturn(List.of(
        new ChoiceDao.Row(courseId, "BS001", null)));
    return d;
  }

  private CourseDao courseDaoWith(String courseId) {
    var d = mock(CourseDao.class);
    when(d.findById(courseId)).thenReturn(Optional.of(
        new CourseDao.Row(courseId, "DB", 32, BigDecimal.valueOf(3),
            "Li", "B101", false)));
    return d;
  }

  private ServerSocket startMockIntegration(Message reply) throws Exception {
    var ss = new ServerSocket(0);
    Thread t = new Thread(() -> {
      try (Socket sock = ss.accept()) {
        Message.read(sock.getInputStream());
        Message.write(sock.getOutputStream(), reply);
      } catch (Exception ignored) {}
    });
    t.setDaemon(true);
    t.start();
    return ss;
  }

  @Test
  void wraps_local_and_cross_choices_into_myChoices() throws Exception {
    var choiceDao = choiceDaoWith("BC001");
    var courseDao = courseDaoWith("BC001");
    try (var integ = startMockIntegration(Message.ok("r", CROSS_RESULT_OK))) {
      System.setProperty("integration.port", Integer.toString(integ.getLocalPort()));
      var config = new CollegeServerConfig("B", "BC", "BS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);

      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>BS001</sno>"));

      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<myChoices sno=\"BS001\" home=\"B\">"));
      assertTrue(res.payload().contains("<编号>BC001</编号>"));
      assertTrue(res.payload().contains("<来源>A</来源>"));
      assertTrue(res.payload().contains("<编号>AC001</编号>"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void integration_unreachable_keeps_home_block_and_records_error() {
    var choiceDao = choiceDaoWith("BC001");
    var courseDao = courseDaoWith("BC001");
    System.setProperty("integration.port", "1");
    try {
      var config = new CollegeServerConfig("B", "BC", "BS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);
      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>BS001</sno>"));
      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<编号>BC001</编号>"));
      assertTrue(res.payload().contains("<error college=\"*\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void local_dao_failure_returns_err() {
    var choiceDao = mock(ChoiceDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db"));
    var h = new ListMyChoicesHandler(choiceDao, mock(CourseDao.class),
        new CollegeServerConfig("B", "BC", "BS"));
    var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>BS001</sno>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }
}
