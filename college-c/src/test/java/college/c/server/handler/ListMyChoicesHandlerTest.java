package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import college.c.server.CollegeServerConfig;
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
      + "<sno>CS001</sno><grade></grade></class></classes>"
      + "<errors/></crossEnrolledResult>";

  private ChoiceDao choiceDaoWith(String courseId) {
    var d = mock(ChoiceDao.class);
    when(d.findByStudent("CS001")).thenReturn(List.of(
        new ChoiceDao.Row(courseId, "CS001", null)));
    return d;
  }

  private CourseDao courseDaoWith(String courseId) {
    var d = mock(CourseDao.class);
    when(d.findById(courseId)).thenReturn(Optional.of(
        new CourseDao.Row(courseId, "Net", 32, BigDecimal.valueOf(2),
            "Wang", "C301", false)));
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
    var choiceDao = choiceDaoWith("CC001");
    var courseDao = courseDaoWith("CC001");
    try (var integ = startMockIntegration(Message.ok("r", CROSS_RESULT_OK))) {
      System.setProperty("integration.port", Integer.toString(integ.getLocalPort()));
      var config = new CollegeServerConfig("C", "CC", "CS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);
      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>CS001</sno>"));
      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<myChoices sno=\"CS001\" home=\"C\">"));
      assertTrue(res.payload().contains("<Cno>CC001</Cno>"));
      assertTrue(res.payload().contains("<class origin=\"A\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }

  @Test
  void integration_unreachable_keeps_home_block_and_records_error() {
    var choiceDao = choiceDaoWith("CC001");
    var courseDao = courseDaoWith("CC001");
    System.setProperty("integration.port", "1");
    try {
      var config = new CollegeServerConfig("C", "CC", "CS");
      var h = new ListMyChoicesHandler(choiceDao, courseDao, config);
      var res = h.handle(new Message(Command.LIST_MY_CHOICES, "r1", "<sno>CS001</sno>"));
      assertEquals(Command.OK, res.command());
      assertTrue(res.payload().contains("<Cno>CC001</Cno>"));
      assertTrue(res.payload().contains("<error college=\"*\">"));
    } finally {
      System.clearProperty("integration.port");
    }
  }
}
