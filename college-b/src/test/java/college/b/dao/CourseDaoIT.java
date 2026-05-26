package college.b.dao;

import college.b.jdbc.JdbcFactory;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CourseDaoIT {
  static CourseDao dao;

  @BeforeAll
  static void setup() throws Exception {
    dao = new CourseDao(JdbcFactory.fromClasspath("/db.properties"));
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 课程 WHERE 编号 LIKE 'IT_%'");
      st.execute("INSERT INTO 课程(编号,名称,课时,学分,老师,地点,共享) VALUES('ITC01','测试课甲',32,2,'李老师','B101','Y')");
      st.execute("INSERT INTO 课程(编号,名称,课时,学分,老师,地点,共享) VALUES('ITC02','测试课乙',48,3,'王老师','B102','N')");
      c.commit();
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 课程 WHERE 编号 LIKE 'IT_%'");
      c.commit();
    }
  }

  @Test @Order(1)
  void findById() {
    var c = dao.findById("ITC01").orElseThrow();
    assertEquals("测试课甲", c.name());
    assertEquals(0, BigDecimal.valueOf(2).compareTo(c.score()));
    assertTrue(c.shared());
  }

  @Test @Order(2)
  void findShared_only_Y() {
    List<CourseDao.Row> shared = dao.findShared();
    assertTrue(shared.stream().anyMatch(r -> r.id().equals("ITC01")));
    assertTrue(shared.stream().noneMatch(r -> r.id().equals("ITC02")));
  }
}
