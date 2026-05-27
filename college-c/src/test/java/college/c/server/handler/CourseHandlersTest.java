package college.c.server.handler;

import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import college.c.server.CollegeServerConfig;
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
      new CollegeServerConfig("C", "CC", "CS");

  @Test void list_local_returns_c_format_xml() {
    var dao = mock(CourseDao.class);
    when(dao.findAll()).thenReturn(List.of(
        new CourseDao.Row("CC001","数据库",32,new BigDecimal("3.0"),"李老师","C101",true)));
    var res = new ListLocalCoursesHandler(dao).handle(new Message(Command.LIST_LOCAL_COURSES,"r1",""));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<Cno>CC001</Cno>"));
  }

  @Test void enroll_local_inserts() {
    var courseDao = mock(CourseDao.class);
    var choiceDao = mock(ChoiceDao.class);
    when(courseDao.findById("CC001")).thenReturn(Optional.of(
        new CourseDao.Row("CC001","DB",32,new BigDecimal("3.0"),"Li","C101",false)));
    when(choiceDao.exists("CS001","CC001")).thenReturn(false);
    var res = new EnrollLocalHandler(courseDao, choiceDao, CONFIG).handle(
        new Message(Command.ENROLL,"r1","<choice><Cno>CC001</Cno><Sno>CS001</Sno></choice>"));
    assertEquals(Command.OK, res.command());
    verify(choiceDao).enroll("CS001","CC001");
  }

  @Test void enroll_returns_err_when_already() {
    var courseDao = mock(CourseDao.class);
    var choiceDao = mock(ChoiceDao.class);
    when(courseDao.findById("CC001")).thenReturn(Optional.of(
        new CourseDao.Row("CC001","DB",32,new BigDecimal("3.0"),"Li","C101",false)));
    when(choiceDao.exists("CS001","CC001")).thenReturn(true);
    var res = new EnrollLocalHandler(courseDao, choiceDao, CONFIG).handle(
        new Message(Command.ENROLL,"r1","<choice><Cno>CC001</Cno><Sno>CS001</Sno></choice>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("ALREADY_ENROLLED"));
  }

  @Test void withdraw_success() {
    var dao = mock(ChoiceDao.class);
    when(dao.withdraw("CS001","CC001")).thenReturn(1);
    var res = new WithdrawLocalHandler(dao, CONFIG).handle(
        new Message(Command.WITHDRAW,"r1","<choice><Cno>CC001</Cno><Sno>CS001</Sno></choice>"));
    assertEquals(Command.OK, res.command());
  }

  @Test void withdraw_no_record() {
    var dao = mock(ChoiceDao.class);
    when(dao.withdraw("CS001","CC001")).thenReturn(0);
    var res = new WithdrawLocalHandler(dao, CONFIG).handle(
        new Message(Command.WITHDRAW,"r1","<choice><Cno>CC001</Cno><Sno>CS001</Sno></choice>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("NO_SUCH_CHOICE"));
  }
}
