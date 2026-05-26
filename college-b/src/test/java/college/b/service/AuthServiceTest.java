package college.b.service;

import college.b.dao.AccountDao;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
  @Test
  void login_success() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(
        Optional.of(new AccountDao.Account("u", "p", 1, null)));
    var svc = new AuthService(dao);
    var s = svc.login("u", "p").orElseThrow();
    assertEquals("u", s.username());
    assertEquals("stu", s.role());
  }

  @Test
  void login_admin() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(
        Optional.of(new AccountDao.Account("u", "p", 5, null)));
    var s = new AuthService(dao).login("u", "p").orElseThrow();
    assertEquals("adm", s.role());
  }

  @Test
  void login_wrong_password_empty() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(
        Optional.of(new AccountDao.Account("u", "p", 1, null)));
    assertTrue(new AuthService(dao).login("u", "x").isEmpty());
  }

  @Test
  void login_unknown_empty() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("x")).thenReturn(Optional.empty());
    assertTrue(new AuthService(dao).login("x", "p").isEmpty());
  }
}
