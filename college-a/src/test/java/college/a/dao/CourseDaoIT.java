package college.a.dao;

import college.a.jdbc.JdbcFactory;
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
      st.execute("DELETE FROM 课程 WHERE 课程编号 LIKE 'IT_%'");
      st.execute("INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) " +
                 "VALUES('IT_C01','测试课甲',32,'2','李老师','A101','Y')");
      st.execute("INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) " +
                 "VALUES('IT_C02','测试课乙',16,'1','王老师','A102','N')");
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 课程 WHERE 课程编号 LIKE 'IT_%'");
    }
  }

  @Test @Order(1)
  void findById() {
    var c = dao.findById("IT_C01").orElseThrow();
    assertEquals("测试课甲", c.name());
    assertEquals(0, BigDecimal.valueOf(2.0).compareTo(c.score()));
    assertTrue(c.shared());
  }

  @Test @Order(2)
  void findShared_only_Y() {
    List<CourseDao.Row> shared = dao.findShared();
    assertTrue(shared.stream().anyMatch(r -> r.id().equals("IT_C01")));
    assertTrue(shared.stream().noneMatch(r -> r.id().equals("IT_C02")));
  }
}
