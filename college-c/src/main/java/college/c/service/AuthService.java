package college.c.service;

import college.c.dao.AccountDao;
import java.util.Optional;

public class AuthService {
  public record Session(String username, String role) {}

  private final AccountDao dao;
  public AuthService(AccountDao dao) { this.dao = dao; }

  public Optional<Session> login(String user, String pass) {
    return dao.findByUsername(user)
        .filter(a -> a.password().equals(pass))
        .map(a -> new Session(a.username(), "admin".equalsIgnoreCase(a.username()) ? "adm" : "stu"));
  }
}
