package college.a.dao;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentDao {
  public record Row(String id, String name, String sex, String dept, String accountRef) {}

  private final DataSource ds;

  public StudentDao(DataSource ds) { this.ds = ds; }

  public Optional<Row> findById(String id) {
    String sql = "SELECT 学号,姓名,性别,院系,关联账户 FROM 学生 WHERE 学号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(map(rs));
      }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findAll() {
    String sql = "SELECT 学号,姓名,性别,院系,关联账户 FROM 学生";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(map(rs));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public boolean insertIfMissing(Row r) {
    String sql = "INSERT INTO 学生(学号,姓名,性别,院系,关联账户) " +
                 "SELECT ?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM 学生 WHERE 学号=?)";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, r.id()); ps.setString(2, r.name());
      ps.setString(3, r.sex()); ps.setString(4, r.dept());
      ps.setString(5, r.accountRef()); ps.setString(6, r.id());
      return ps.executeUpdate() == 1;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  private static Row map(java.sql.ResultSet rs) throws SQLException {
    return new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
  }
}
