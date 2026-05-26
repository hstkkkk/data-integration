// college-a/src/test/java/college/a/dao/ChoiceDaoIT.java
package college.a.dao;

import college.a.jdbc.JdbcFactory;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChoiceDaoIT {
  static ChoiceDao dao;

  @BeforeAll
  static void setup() throws Exception {
    dao = new ChoiceDao(JdbcFactory.fromClasspath("/db.properties"));
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 选课  WHERE 学生编号='IT_S' OR 课程编号='IT_C'");
      st.execute("DELETE FROM 学生  WHERE 学号='IT_S'");
      st.execute("DELETE FROM 课程  WHERE 课程编号='IT_C'");
      st.execute("INSERT INTO 学生(学号,姓名,性别,院系,关联账户) VALUES('IT_S','测试','男','计算机',NULL)");
      st.execute("INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) " +
                 "VALUES('IT_C','测试课',16,'1','X','A101','N')");
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 选课 WHERE 学生编号='IT_S' OR 课程编号='IT_C'");
      st.execute("DELETE FROM 学生 WHERE 学号='IT_S'");
      st.execute("DELETE FROM 课程 WHERE 课程编号='IT_C'");
    }
  }

  @Test @Order(1)
  void enroll_local_inserts() {
    int rows = dao.enrollLocal("IT_S", "IT_C");
    assertEquals(1, rows);
    assertTrue(dao.exists("IT_S", "IT_C"));
  }

  @Test @Order(2)
  void enroll_duplicate_throws() {
    assertThrows(RuntimeException.class, () -> dao.enrollLocal("IT_S", "IT_C"));
  }

  @Test @Order(3)
  void withdraw_removes() {
    int rows = dao.withdraw("IT_S", "IT_C");
    assertEquals(1, rows);
    assertFalse(dao.exists("IT_S", "IT_C"));
  }
}
