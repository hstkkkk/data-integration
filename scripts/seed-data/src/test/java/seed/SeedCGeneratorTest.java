package seed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedCGeneratorTest {
  @Test void generates_50_10_250() {
    var d = SeedC.generate(42L);
    assertEquals(50, d.students().size());
    assertEquals(10, d.courses().size());
    assertEquals(250, d.choices().size());
  }
  @Test void ids_prefixed() {
    var d = SeedC.generate(1L);
    assertTrue(d.students().stream().allMatch(s -> s.id().startsWith("CS")));
    assertTrue(d.courses().stream().allMatch(c -> c.id().startsWith("CC")));
  }
  @Test void to_sql_ok() {
    String sql = SeedC.toSql(SeedC.generate(1L));
    assertTrue(sql.contains("INSERT INTO 账户"));
    assertTrue(sql.contains("INSERT INTO 学生"));
    assertTrue(sql.contains("INSERT INTO 课程"));
    assertTrue(sql.contains("INSERT INTO 选课"));
  }
}
