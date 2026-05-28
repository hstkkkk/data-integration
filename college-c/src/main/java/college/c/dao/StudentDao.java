package college.c.dao;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentDao {
  public record Row(String id, String name, String sex, String dept, String password) {}

  private final DataSource ds;
  public StudentDao(DataSource ds) { this.ds = ds; }

  public Optional<Row> findById(String id) {
    String sql = "SELECT Sno, Snm, Sex, Sde, Pwd FROM 学生 WHERE Sno = ?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      try (var rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findAll() {
    String sql = "SELECT Sno, Snm, Sex, Sde, Pwd FROM 学生";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(map(rs));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public boolean insertIfMissing(Row r) {
    String sql = "INSERT IGNORE INTO 学生(Sno, Snm, Sex, Sde, Pwd) VALUES (?,?,?,?,?)";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, r.id()); ps.setString(2, r.name());
      ps.setString(3, r.sex()); ps.setString(4, r.dept());
      ps.setString(5, r.password());
      return ps.executeUpdate() == 1;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public boolean updateProfile(String id, String name, String sex, String dept) {
    String sql = "UPDATE 学生 SET Snm=?, Sex=?, Sde=? WHERE Sno=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, name);
      ps.setString(2, sex);
      ps.setString(3, dept);
      ps.setString(4, id);
      return ps.executeUpdate() == 1;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  private static Row map(java.sql.ResultSet rs) throws SQLException {
    return new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
  }
}
