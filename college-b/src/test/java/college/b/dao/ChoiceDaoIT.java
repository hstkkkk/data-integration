package college.b.dao;

import college.b.jdbc.JdbcFactory;
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
      st.execute("DELETE FROM 选课 WHERE 学号='IT_S' OR 课程编号='IT_C'");
      st.execute("DELETE FROM 学生 WHERE 学号='IT_S'");
      st.execute("DELETE FROM 课程 WHERE 编号='IT_C'");
      st.execute("INSERT INTO 学生(学号,姓名,性别,专业,密码) VALUES('IT_S','测试','男','计算机','pw')");
      st.execute("INSERT INTO 课程(编号,名称,课时,学分,老师,地点,共享) VALUES('IT_C','测试课',32,2,'X','B101','N')");
      c.commit();
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 选课 WHERE 学号='IT_S' OR 课程编号='IT_C'");
      st.execute("DELETE FROM 学生 WHERE 学号='IT_S'");
      st.execute("DELETE FROM 课程 WHERE 编号='IT_C'");
      c.commit();
    }
  }

  @Test @Order(1)
  void enroll_inserts() {
    int rows = dao.enroll("IT_S", "IT_C");
    assertEquals(1, rows);
    assertTrue(dao.exists("IT_S", "IT_C"));
  }

  @Test @Order(2)
  void enroll_duplicate_throws() {
    assertThrows(RuntimeException.class, () -> dao.enroll("IT_S", "IT_C"));
  }

  @Test @Order(3)
  void withdraw_removes() {
    int rows = dao.withdraw("IT_S", "IT_C");
    assertEquals(1, rows);
    assertFalse(dao.exists("IT_S", "IT_C"));
  }
}
