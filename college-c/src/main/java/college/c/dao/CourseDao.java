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

  public List<Row> findExportableShared() {
    return query("SELECT Cno, Cnm, Ctm, Cpt, Tec, Pla, Share FROM 课程 WHERE Share = 'Y' AND Cno LIKE 'CC%'");
  }

  public int upsertShared(List<Row> rows) {
    int count = 0;
    for (Row row : rows) {
      count += upsertShared(row);
    }
    return count;
  }

  private int upsertShared(Row row) {
    String sql = "INSERT INTO 课程(Cno,Cnm,Ctm,Cpt,Tec,Pla,Share) VALUES(?,?,?,?,?,?,?) "
        + "ON DUPLICATE KEY UPDATE Cnm=VALUES(Cnm), Ctm=VALUES(Ctm), Cpt=VALUES(Cpt), "
        + "Tec=VALUES(Tec), Pla=VALUES(Pla), Share=VALUES(Share)";
    try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
      ps.setString(1, row.id());
      ps.setString(2, row.name());
      ps.setInt(3, row.hours());
      ps.setBigDecimal(4, row.score());
      ps.setString(5, row.teacher());
      ps.setString(6, row.location());
      ps.setString(7, row.shared() ? "Y" : "N");
      return ps.executeUpdate();
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
