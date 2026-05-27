package college.a.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.server.CollegeServerConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CourseHandlersTest {

  private static final CollegeServerConfig CONFIG =
      new CollegeServerConfig("A", "AC", "AS");

  @Test
  void list_local_returns_a_format_xml() {
    var courseDao = mock(CourseDao.class);
    when(courseDao.findAll()).thenReturn(List.of(
        new CourseDao.Row("AC001", "Math", 48, BigDecimal.valueOf(3),
            "Prof. Li", "Room 101", false)));
    var h = new ListLocalCoursesHandler(courseDao);

    var res = h.handle(new Message(Command.LIST_LOCAL_COURSES, "r1", ""));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<课程编号>AC001</课程编号>"));
  }

  @Test
  void enroll_local_inserts_when_course_present() {
    var courseDao = mock(CourseDao.class);
    when(courseDao.findById("AC001")).thenReturn(Optional.of(
        new CourseDao.Row("AC001", "Math", 48, BigDecimal.valueOf(3),
            "Prof. Li", "Room 101", false)));
    var choiceDao = mock(ChoiceDao.class);
    when(choiceDao.exists("AS001", "AC001")).thenReturn(false);
    when(choiceDao.enrollLocal("AS001", "AC001")).thenReturn(1);
    var h = new EnrollLocalHandler(courseDao, choiceDao, CONFIG);

    var res = h.handle(new Message(Command.ENROLL, "r2",
        "<选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号></选课>"));

    assertEquals(Command.OK, res.command());
    verify(choiceDao).exists("AS001", "AC001");
    verify(choiceDao).enrollLocal("AS001", "AC001");
  }

  @Test
  void enroll_returns_err_when_already_enrolled() {
    var courseDao = mock(CourseDao.class);
    when(courseDao.findById("AC001")).thenReturn(Optional.of(
        new CourseDao.Row("AC001", "Math", 48, BigDecimal.valueOf(3),
            "Prof. Li", "Room 101", false)));
    var choiceDao = mock(ChoiceDao.class);
    when(choiceDao.exists("AS001", "AC001")).thenReturn(true);
    var h = new EnrollLocalHandler(courseDao, choiceDao, CONFIG);

    var res = h.handle(new Message(Command.ENROLL, "r3",
        "<选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号></选课>"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("ALREADY_ENROLLED"));
  }

  @Test
  void withdraw_returns_err_when_no_record() {
    var choiceDao = mock(ChoiceDao.class);
    when(choiceDao.withdraw("AS001", "AC001")).thenReturn(0);
    var h = new WithdrawLocalHandler(choiceDao, CONFIG);

    var res = h.handle(new Message(Command.WITHDRAW, "r4",
        "<选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号></选课>"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("NO_SUCH_CHOICE"));
  }

  @Test
  void enroll_returns_err_when_course_not_found() {
    var courseDao = mock(CourseDao.class);
    when(courseDao.findById("AC001")).thenReturn(Optional.empty());
    var choiceDao = mock(ChoiceDao.class);
    var h = new EnrollLocalHandler(courseDao, choiceDao, CONFIG);

    var res = h.handle(new Message(Command.ENROLL, "r5",
        "<选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号></选课>"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("NO_SUCH_COURSE"));
  }

  @Test
  void withdraw_success_returns_ok() {
    var choiceDao = mock(ChoiceDao.class);
    when(choiceDao.withdraw("AS001", "AC001")).thenReturn(1);
    var h = new WithdrawLocalHandler(choiceDao, CONFIG);

    var res = h.handle(new Message(Command.WITHDRAW, "r6",
        "<选课><课程编号>AC001</课程编号><学生编号>AS001</学生编号></选课>"));

    assertEquals(Command.OK, res.command());
    verify(choiceDao).withdraw("AS001", "AC001");
  }

  @Test
  void enroll_bad_payload_returns_err() {
    var courseDao = mock(CourseDao.class);
    var choiceDao = mock(ChoiceDao.class);
    var h = new EnrollLocalHandler(courseDao, choiceDao, CONFIG);

    var res = h.handle(new Message(Command.ENROLL, "r7", "not valid xml"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("BAD_PAYLOAD"));
  }

  @Test
  void list_local_returns_empty_when_no_courses() {
    var courseDao = mock(CourseDao.class);
    when(courseDao.findAll()).thenReturn(List.of());
    var h = new ListLocalCoursesHandler(courseDao);

    var res = h.handle(new Message(Command.LIST_LOCAL_COURSES, "r8", ""));

    assertEquals(Command.OK, res.command());
  }
}
