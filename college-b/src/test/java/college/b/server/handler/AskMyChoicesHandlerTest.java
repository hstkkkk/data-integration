package college.b.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AskMyChoicesHandlerTest {
  @Test
  void returns_local_field_xml_for_student_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("BS001")).thenReturn(List.of(
        new ChoiceDao.Row("BC003", "BS001", null)));
    when(courseDao.findById("BC003")).thenReturn(Optional.of(
        new CourseDao.Row("BC003", "OS", 32, BigDecimal.valueOf(3),
            "Wang", "B201", true)));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>BS001</sno>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"BS001\">"));
    assertTrue(res.payload().contains("<编号>BC003</编号>"));
    assertTrue(res.payload().contains("<老师>Wang</老师>"));
    assertTrue(res.payload().contains("<学号>BS001</学号>"));
  }

  @Test
  void returns_empty_set_when_no_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("BS999")).thenReturn(List.of());
    var h = new AskMyChoicesHandler(choiceDao, courseDao);
    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>BS999</sno>"));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"BS999\">"));
  }

  @Test
  void returns_err_on_dao_failure() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db down"));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);
    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>BS001</sno>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }
}
