package college.c.service;

import college.c.dao.AccountDao;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
  @Test void login_success() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(Optional.of(new AccountDao.Account("u","p")));
    var s = new AuthService(dao).login("u","p").orElseThrow();
    assertEquals("u", s.username());
    assertEquals("stu", s.role());
  }
  @Test void login_wrong_password() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("u")).thenReturn(Optional.of(new AccountDao.Account("u","p")));
    assertTrue(new AuthService(dao).login("u","x").isEmpty());
  }
  @Test void login_unknown() {
    var dao = mock(AccountDao.class);
    when(dao.findByUsername("x")).thenReturn(Optional.empty());
    assertTrue(new AuthService(dao).login("x","p").isEmpty());
  }
}
