package college.b.dao;

import college.b.jdbc.JdbcFactory;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountDaoIT {
  static AccountDao dao;

  @BeforeAll
  static void setup() throws Exception {
    var ds = JdbcFactory.fromClasspath("/db.properties");
    dao = new AccountDao(ds);
    try (var c = ds.getConnection(); var st = c.createStatement()) {
      st.execute("DELETE FROM 账户 WHERE 账户名='unit_test'");
      st.execute("INSERT INTO 账户(账户名,密码,级别) VALUES('unit_test','pwd123',1)");
      c.commit();
    }
  }

  @Test @Order(1)
  void findByUsername_returns_account() {
    var acc = dao.findByUsername("unit_test").orElseThrow();
    assertEquals("pwd123", acc.password());
    assertEquals(1, acc.level());
  }

  @Test @Order(2)
  void findByUsername_unknown_returns_empty() {
    assertTrue(dao.findByUsername("__nope__").isEmpty());
  }

  @AfterAll
  static void teardown() throws Exception {
    try (var c = JdbcFactory.fromClasspath("/db.properties").getConnection();
         var st = c.createStatement()) {
      st.execute("DELETE FROM 账户 WHERE 账户名='unit_test'");
      c.commit();
    }
  }
}
