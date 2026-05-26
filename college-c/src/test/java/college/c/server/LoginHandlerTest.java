package college.c.server;

import college.c.dao.AccountDao;
import college.c.server.handler.LoginHandler;
import college.c.service.AuthService;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginHandlerTest {
  @Test void login_ok() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(Optional.of(new AccountDao.Account("u","p")));
    var res = new LoginHandler(new AuthService(dao)).handle(
        new Message(Command.LOGIN, "r1", "<login><user>u</user><pass>p</pass></login>"));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<role>stu</role>"));
  }

  @Test void login_bad_password() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(Optional.of(new AccountDao.Account("u","p")));
    var res = new LoginHandler(new AuthService(dao)).handle(
        new Message(Command.LOGIN, "r2", "<login><user>u</user><pass>x</pass></login>"));
    assertEquals(Command.ERR, res.command());
  }
}
