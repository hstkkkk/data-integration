package college.b.dao;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChoiceDao {
  public record Row(String courseId, String studentId, String score) {}

  private final DataSource ds;

  public ChoiceDao(DataSource ds) { this.ds = ds; }

  public int enroll(String studentId, String courseId) {
    return insert(studentId, courseId, "B");
  }

  public int enrollFromOther(String studentId, String courseId, String origin) {
    return insert(studentId, courseId, origin);
  }

  private int insert(String studentId, String courseId, String origin) {
    String sql = "INSERT INTO 选课(课程编号,学号,来源) VALUES(?,?,?)";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, courseId); ps.setString(2, studentId); ps.setString(3, origin);
      return ps.executeUpdate();
    } catch (SQLException e) { throw new RuntimeException("enroll failed", e); }
  }

  public int withdraw(String studentId, String courseId) {
    String sql = "DELETE FROM 选课 WHERE 学号=? AND 课程编号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, studentId); ps.setString(2, courseId);
      return ps.executeUpdate();
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public boolean exists(String studentId, String courseId) {
    String sql = "SELECT 1 FROM 选课 WHERE 学号=? AND 课程编号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, studentId); ps.setString(2, courseId);
      try (var rs = ps.executeQuery()) { return rs.next(); }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findAll() {
    String sql = "SELECT 课程编号,学号,得分 FROM 选课";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3)));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findByStudent(String studentId) {
    String sql = "SELECT 课程编号,学号,得分 FROM 选课 WHERE 学号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, studentId);
      try (var rs = ps.executeQuery()) {
        List<Row> out = new ArrayList<>();
        while (rs.next()) out.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3)));
        return out;
      }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }
}
