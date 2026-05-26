package college.c.dao;

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
    String sql = "SELECT Cno, Cnm, Ctm, Cpt, Tec, Pla, Share FROM 课程 WHERE Cno = ?";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, id);
      try (var rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
    } catch (SQLException e) { throw new RuntimeException(e); }
  }

  public List<Row> findAll() {
    return query("SELECT Cno, Cnm, Ctm, Cpt, Tec, Pla, Share FROM 课程");
  }

  public List<Row> findShared() {
    return query("SELECT Cno, Cnm, Ctm, Cpt, Tec, Pla, Share FROM 课程 WHERE Share = 'Y'");
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
