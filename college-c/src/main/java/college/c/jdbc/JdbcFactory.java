package college.c.jdbc;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public final class JdbcFactory {
  private JdbcFactory() {}

  public static DataSource fromClasspath(String resource) throws IOException {
    Properties p = new Properties();
    try (var in = JdbcFactory.class.getResourceAsStream(resource)) {
      if (in == null) throw new IOException("resource not found: " + resource);
      p.load(in);
    }
    return new SimpleDataSource(
        p.getProperty("jdbc.url"), p.getProperty("jdbc.user"), p.getProperty("jdbc.password"));
  }

  private record SimpleDataSource(String url, String user, String pass) implements DataSource {
    @Override public Connection getConnection() throws SQLException {
      return DriverManager.getConnection(url, user, pass);
    }
    @Override public Connection getConnection(String u, String p) throws SQLException {
      return DriverManager.getConnection(url, u, p);
    }
    @Override public PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(PrintWriter out) {}
    @Override public void setLoginTimeout(int seconds) {}
    @Override public int getLoginTimeout() { return 0; }
    @Override public Logger getParentLogger() { return Logger.getLogger("jdbc"); }
    @SuppressWarnings("unchecked")
    @Override public <T> T unwrap(Class<T> i) { throw new UnsupportedOperationException(); }
    @Override public boolean isWrapperFor(Class<?> i) { return false; }
  }
}
