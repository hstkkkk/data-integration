package seed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedBGeneratorTest {
  @Test
  void generates_50_students_10_courses_250_choices() {
    var data = SeedB.generate(42L);
    assertEquals(50, data.students().size());
    assertEquals(10, data.courses().size());
    assertEquals(250, data.choices().size());
    assertTrue(data.courses().stream().filter(c -> c.shared()).count() >= 3);
  }

  @Test
  void student_ids_unique_and_prefixed() {
    var data = SeedB.generate(1L);
    assertEquals(50, data.students().stream().map(s -> s.id()).distinct().count());
    assertTrue(data.students().stream().allMatch(s -> s.id().startsWith("BS")));
  }

  @Test
  void to_sql_contains_inserts() {
    String sql = SeedB.toSql(SeedB.generate(1L));
    assertTrue(sql.contains("INSERT INTO 账户"));
    assertTrue(sql.contains("INSERT INTO 学生"));
    assertTrue(sql.contains("INSERT INTO 课程"));
    assertTrue(sql.contains("INSERT INTO 选课"));
  }
}
