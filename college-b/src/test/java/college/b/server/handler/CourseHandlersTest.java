package college.b.server.handler;

import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import college.b.server.CollegeServerConfig;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseHandlersTest {

  private static final CollegeServerConfig CONFIG =
      new CollegeServerConfig("B", "BC", "BS");

  @Test
  void list_local_returns_b_format_xml() {
    var dao = mock(CourseDao.class);
    when(dao.findAll()).thenReturn(List.of(
        new CourseDao.Row("BC001", "数据库", 32, new BigDecimal("3.0"), "李老师", "B101", true)));
    var res = new ListLocalCoursesHandler(dao).handle(new Message(Command.LIST_LOCAL_COURSES, "r1", ""));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<编号>BC001</编号>"));
  }

  @Test
  void enroll_local_inserts_when_course_present() {
    var courseDao = mock(CourseDao.class);
    var choiceDao = mock(ChoiceDao.class);
    when(courseDao.findById("BC001")).thenReturn(Optional.of(
        new CourseDao.Row("BC001", "数据库", 32, new BigDecimal("3.0"), "李老师", "B101", false)));
    when(choiceDao.exists("BS001", "BC001")).thenReturn(false);

    var res = new EnrollLocalHandler(courseDao, choiceDao, CONFIG).handle(
        new Message(Command.ENROLL, "r1",
            "<选课><课程编号>BC001</课程编号><学号>BS001</学号></选课>"));
    assertEquals(Command.OK, res.command());
    verify(choiceDao).exists("BS001", "BC001");
    verify(choiceDao).enroll("BS001", "BC001");
  }

  @Test
  void enroll_returns_err_when_course_not_found() {
    var courseDao = mock(CourseDao.class);
    var choiceDao = mock(ChoiceDao.class);
    when(courseDao.findById("BC999")).thenReturn(Optional.empty());
    var res = new EnrollLocalHandler(courseDao, choiceDao, CONFIG).handle(
        new Message(Command.ENROLL, "r1",
            "<选课><课程编号>BC999</课程编号><学号>BS001</学号></选课>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("NO_SUCH_COURSE"));
  }

  @Test
  void enroll_returns_err_when_already_enrolled() {
    var courseDao = mock(CourseDao.class);
    var choiceDao = mock(ChoiceDao.class);
    when(courseDao.findById("BC001")).thenReturn(Optional.of(
        new CourseDao.Row("BC001", "数据库", 32, new BigDecimal("3.0"), "李老师", "B101", false)));
    when(choiceDao.exists("BS001", "BC001")).thenReturn(true);
    var res = new EnrollLocalHandler(courseDao, choiceDao, CONFIG).handle(
        new Message(Command.ENROLL, "r1",
            "<选课><课程编号>BC001</课程编号><学号>BS001</学号></选课>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("ALREADY_ENROLLED"));
  }

  @Test
  void withdraw_success_returns_ok() {
    var dao = mock(ChoiceDao.class);
    when(dao.withdraw("BS001", "BC001")).thenReturn(1);
    var res = new WithdrawLocalHandler(dao, CONFIG).handle(
        new Message(Command.WITHDRAW, "r1",
            "<选课><课程编号>BC001</课程编号><学号>BS001</学号></选课>"));
    assertEquals(Command.OK, res.command());
  }

  @Test
  void withdraw_returns_err_when_no_record() {
    var dao = mock(ChoiceDao.class);
    when(dao.withdraw("BS001", "BC001")).thenReturn(0);
    var res = new WithdrawLocalHandler(dao, CONFIG).handle(
        new Message(Command.WITHDRAW, "r1",
            "<选课><课程编号>BC001</课程编号><学号>BS001</学号></选课>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("NO_SUCH_CHOICE"));
  }
}
