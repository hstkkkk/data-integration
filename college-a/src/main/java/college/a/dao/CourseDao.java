package college.a.dao;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CourseDao {
  public record Row(String id, String name, int hours, BigDecimal score,
                    String teacher, String location, boolean shared) {}

  private final DataSource ds;

  public CourseDao(DataSource ds) { this.ds = ds; }

  public Optional<Row> findById(String id) {
    String sql = "SELECT 课程编号,课程名称,课时,学分,授课老师,授课地点,共享 FROM 课程 WHERE 课程编号=?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      try (var rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(map(rs));
      }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findAll() {
    return query("SELECT 课程编号,课程名称,课时,学分,授课老师,授课地点,共享 FROM 课程");
  }

  public List<Row> findShared() {
    return query("SELECT 课程编号,课程名称,课时,学分,授课老师,授课地点,共享 FROM 课程 WHERE 共享='Y'");
  }

  public List<Row> findExportableShared() {
    return query("SELECT 课程编号,课程名称,课时,学分,授课老师,授课地点,共享 FROM 课程 WHERE 共享='Y' AND 课程编号 LIKE 'AC%'");
  }

  public int upsertShared(List<Row> rows) {
    int count = 0;
    for (Row row : rows) {
      count += upsertShared(row);
    }
    return count;
  }

  private int upsertShared(Row row) {
    String update = "UPDATE 课程 SET 课程名称=?, 课时=?, 学分=?, 授课老师=?, 授课地点=?, 共享=? WHERE 课程编号=?";
    String insert = "INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) VALUES(?,?,?,?,?,?,?)";
    try (var c = ds.getConnection()) {
      try (var ps = c.prepareStatement(update)) {
        ps.setString(1, row.name());
        ps.setInt(2, row.hours());
        ps.setBigDecimal(3, row.score());
        ps.setString(4, row.teacher());
        ps.setString(5, row.location());
        ps.setString(6, row.shared() ? "Y" : "N");
        ps.setString(7, row.id());
        int updated = ps.executeUpdate();
        if (updated > 0) return updated;
      }
      try (var ps = c.prepareStatement(insert)) {
        ps.setString(1, row.id());
        ps.setString(2, row.name());
        ps.setInt(3, row.hours());
        ps.setBigDecimal(4, row.score());
        ps.setString(5, row.teacher());
        ps.setString(6, row.location());
        ps.setString(7, row.shared() ? "Y" : "N");
        return ps.executeUpdate();
      }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  private List<Row> query(String sql) {
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql); var rs = ps.executeQuery()) {
      List<Row> out = new ArrayList<>();
      while (rs.next()) out.add(map(rs));
      return out;
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  private static Row map(java.sql.ResultSet rs) throws SQLException {
    return new Row(rs.getString(1), rs.getString(2), rs.getInt(3),
                   rs.getBigDecimal(4), rs.getString(5), rs.getString(6),
                   "Y".equalsIgnoreCase(rs.getString(7)));
  }
}
