package college.c.server;

import college.c.dao.AccountDao;
import college.c.dao.ChoiceDao;
import college.c.dao.CourseDao;
import college.c.dao.StudentDao;
import college.c.jdbc.JdbcFactory;
import college.c.server.handler.ApplyChoiceHandler;
import college.c.server.handler.EnrollLocalHandler;
import college.c.server.handler.ListLocalCoursesHandler;
import college.c.server.handler.ListSharedCoursesHandler;
import college.c.server.handler.LoginHandler;
import college.c.server.handler.AskCourseInfoHandler;
import college.c.server.handler.RevokeChoiceHandler;
import college.c.server.handler.StatsForwardHandler;
import college.c.server.handler.StatsPullHandler;
import college.c.server.handler.WithdrawLocalHandler;
import college.c.service.AuthService;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class CollegeCServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final CommandRouter router;
  private volatile boolean running = true;

  public CollegeCServer(int port, CommandRouter router) throws IOException {
    this.router = router;
    this.serverSocket = new ServerSocket(port);
  }

  public int getPort() { return serverSocket.getLocalPort(); }

  public void serve() {
    while (running) {
      try {
        Socket client = serverSocket.accept();
        new Thread(() -> handleClient(client)).start();
      } catch (SocketException e) {
        if (!running) break;
      } catch (IOException e) {
        if (!running) break;
      }
    }
  }

  private void handleClient(Socket client) {
    try (client) {
      Message req = Message.read(client.getInputStream());
      Message res = router.dispatch(req);
      Message.write(client.getOutputStream(), res);
    } catch (IOException e) {
      System.err.println("handleClient error: " + e.getMessage());
    }
  }

  @Override public void close() throws IOException {
    running = false;
    serverSocket.close();
  }

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getProperty("port", "9003"));
    DataSource ds = JdbcFactory.fromClasspath("/db.properties");
    var config = new CollegeServerConfig("C", "CC", "CS");
    var accountDao = new AccountDao(ds);
    var courseDao = new CourseDao(ds);
    var choiceDao = new ChoiceDao(ds);
    var studentDao = new StudentDao(ds);

    var router = new CommandRouter()
        .register(Command.LOGIN, new LoginHandler(new AuthService(accountDao)))
        .register(Command.LIST_LOCAL_COURSES, new ListLocalCoursesHandler(courseDao))
        .register(Command.ENROLL, new EnrollLocalHandler(courseDao, choiceDao, config))
        .register(Command.WITHDRAW, new WithdrawLocalHandler(choiceDao, config))
        .register(Command.ASK_COURSE_INFO, new AskCourseInfoHandler(courseDao))
        .register(Command.LIST_SHARED_COURSES,
            new ListSharedCoursesHandler(config.integrationHost, config.integrationPort, "C", "/xsl/AtoC.xsl"))
        .register(Command.APPLY_CHOICE, new ApplyChoiceHandler(courseDao, choiceDao))
        .register(Command.REVOKE_CHOICE, new RevokeChoiceHandler(choiceDao))
        .register(Command.STATS_GLOBAL, new StatsForwardHandler(config))
        .register(Command.STATS_PULL, new StatsPullHandler(studentDao, courseDao, choiceDao, config));

    try (var srv = new CollegeCServer(port, router)) {
      System.out.println("College C server listening on " + srv.getPort());
      srv.serve();
    }
  }
}
