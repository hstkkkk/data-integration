package college.c.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
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
    when(choiceDao.findByStudent("CS001")).thenReturn(List.of(
        new ChoiceDao.Row("CC005", "CS001", null)));
    when(courseDao.findById("CC005")).thenReturn(Optional.of(
        new CourseDao.Row("CC005", "Net", 32, BigDecimal.valueOf(2),
            "Li", "C301", true)));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>CS001</sno>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"CS001\">"));
    assertTrue(res.payload().contains("<Cno>CC005</Cno>"));
    assertTrue(res.payload().contains("<Tec>Li</Tec>"));
    assertTrue(res.payload().contains("<Sno>CS001</Sno>"));
  }

  @Test
  void returns_empty_set_when_no_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("CS999")).thenReturn(List.of());
    var h = new AskMyChoicesHandler(choiceDao, courseDao);
    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>CS999</sno>"));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"CS999\">"));
  }

  @Test
  void returns_err_on_dao_failure() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db down"));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);
    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>CS001</sno>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }
}
