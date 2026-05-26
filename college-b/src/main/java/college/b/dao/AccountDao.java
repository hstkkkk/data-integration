package college.b.dao;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;

public class AccountDao {
  public record Account(String username, String password, int level, String objectRef) {}

  private final DataSource ds;

  public AccountDao(DataSource ds) { this.ds = ds; }

  public Optional<Account> findByUsername(String username) {
    String sql = "SELECT 账户名,密码,级别,客体 FROM 账户 WHERE 账户名=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, username);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(new Account(
            rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4)));
      }
    } catch (SQLException e) {
      throw new RuntimeException("query account failed", e);
    }
  }
}
