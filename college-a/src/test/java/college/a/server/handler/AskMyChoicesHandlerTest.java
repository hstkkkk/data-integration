package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
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
    when(choiceDao.findByStudent("AS001")).thenReturn(List.of(
        new ChoiceDao.Row("AC001", "AS001", null, "A")));
    when(courseDao.findById("AC001")).thenReturn(Optional.of(
        new CourseDao.Row("AC001", "Math", 48, BigDecimal.valueOf(3),
            "Zhao", "A101", false)));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>AS001</sno>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"AS001\">"));
    assertTrue(res.payload().contains("<课程编号>AC001</课程编号>"));
    assertTrue(res.payload().contains("<授课老师>Zhao</授课老师>"));
    assertTrue(res.payload().contains("<学生编号>AS001</学生编号>"));
  }

  @Test
  void returns_empty_set_when_no_choices() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent("AS999")).thenReturn(List.of());
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>AS999</sno>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<myChoiceSet sno=\"AS999\">"));
  }

  @Test
  void returns_err_on_dao_failure() {
    var choiceDao = mock(ChoiceDao.class);
    var courseDao = mock(CourseDao.class);
    when(choiceDao.findByStudent(any())).thenThrow(new RuntimeException("db down"));
    var h = new AskMyChoicesHandler(choiceDao, courseDao);

    var res = h.handle(new Message(Command.ASK_MY_CHOICES, "r1", "<sno>AS001</sno>"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("LOCAL_QUERY_FAILED"));
  }
}
