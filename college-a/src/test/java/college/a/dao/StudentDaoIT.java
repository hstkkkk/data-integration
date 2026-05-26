package college.a.dao;

import college.a.jdbc.JdbcFactory;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudentDaoIT {
  static StudentDao dao;

  @BeforeAll
  static void setup() throws Exception {
    dao = new StudentDao(JdbcFactory.fromClasspath("/db.properties"));
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 学生 WHERE 学号 LIKE 'IT_%'");
      st.execute("INSERT INTO 学生(学号,姓名,性别,院系,关联账户) VALUES('IT_001','测试甲','男','计算机',NULL)");
      st.execute("INSERT INTO 学生(学号,姓名,性别,院系,关联账户) VALUES('IT_002','测试乙','女','计算机',NULL)");
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 学生 WHERE 学号 LIKE 'IT_%'");
    }
  }

  @Test @Order(1)
  void findById_present() {
    var s = dao.findById("IT_001").orElseThrow();
    assertEquals("测试甲", s.name());
  }

  @Test @Order(2)
  void findAll_includes_inserted() {
    List<StudentDao.Row> all = dao.findAll();
    assertTrue(all.stream().anyMatch(r -> r.id().equals("IT_002")));
  }

  @Test @Order(3)
  void insertIfMissing_inserts_once() {
    boolean first = dao.insertIfMissing(new StudentDao.Row("IT_003", "测试丙", "男", "计算机", null));
    boolean second = dao.insertIfMissing(new StudentDao.Row("IT_003", "测试丙", "男", "计算机", null));
    assertTrue(first);
    assertFalse(second);
  }
}
