package college.a.dao;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;

public class AccountDao {
  public record Account(String username, String password, String role) {}

  private final DataSource ds;

  public AccountDao(DataSource ds) { this.ds = ds; }

  public Optional<Account> findByUsername(String username) {
    String sql = "SELECT 账户名,密码,权限 FROM 账户 WHERE 账户名 = ?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, username);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(new Account(rs.getString(1).trim(), rs.getString(2).trim(), rs.getString(3).trim()));
      }
    } catch (SQLException e) {
      throw new RuntimeException("query account failed", e);
    }
  }
}
