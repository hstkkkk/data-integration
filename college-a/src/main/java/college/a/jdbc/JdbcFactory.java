package college.a.jdbc;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public final class JdbcFactory {
  private JdbcFactory() {}

  public static DataSource fromClasspath(String resource) throws IOException {
    Properties p = new Properties();
    try (var in = JdbcFactory.class.getResourceAsStream(resource)) {
      if (in == null) throw new IOException("resource not found: " + resource);
      p.load(in);
    }
    String url = p.getProperty("jdbc.url");
    String user = p.getProperty("jdbc.user");
    String pass = p.getProperty("jdbc.password");
    return new SimpleDataSource(url, user, pass);
  }

  private record SimpleDataSource(String url, String user, String pass) implements DataSource {
    @Override public Connection getConnection() throws java.sql.SQLException {
      return DriverManager.getConnection(url, user, pass);
    }
    @Override public Connection getConnection(String u, String p) throws java.sql.SQLException {
      return DriverManager.getConnection(url, u, p);
    }
    @Override public java.io.PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(java.io.PrintWriter out) {}
    @Override public void setLoginTimeout(int seconds) {}
    @Override public int getLoginTimeout() { return 0; }
    @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getLogger("jdbc"); }
    @Override public <T> T unwrap(Class<T> i) { throw new UnsupportedOperationException(); }
    @Override public boolean isWrapperFor(Class<?> i) { return false; }
  }
}
