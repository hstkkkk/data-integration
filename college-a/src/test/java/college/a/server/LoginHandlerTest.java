package college.a.server;

import college.a.dao.AccountDao;
import college.a.server.handler.LoginHandler;
import college.a.service.AuthService;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginHandlerTest {
  @Test
  void login_ok() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(Optional.of(new AccountDao.Account("u", "p", "stu")));
    var h = new LoginHandler(new AuthService(dao));

    var req = new Message(Command.LOGIN, "r1",
        "<login><user>u</user><pass>p</pass></login>");
    var res = h.handle(req);

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<role>stu</role>"));
  }

  @Test
  void login_bad_password_returns_err() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(Optional.of(new AccountDao.Account("u", "p", "stu")));
    var h = new LoginHandler(new AuthService(dao));
    var res = h.handle(new Message(Command.LOGIN, "r2",
        "<login><user>u</user><pass>x</pass></login>"));
    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("AUTH_FAILED"));
  }
}
