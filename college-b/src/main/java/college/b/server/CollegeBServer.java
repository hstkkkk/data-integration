package college.b.server;

import college.b.dao.AccountDao;
import college.b.dao.ChoiceDao;
import college.b.dao.CourseDao;
import college.b.jdbc.JdbcFactory;
import college.b.server.handler.ApplyChoiceHandler;
import college.b.server.handler.EnrollLocalHandler;
import college.b.server.handler.ListLocalCoursesHandler;
import college.b.server.handler.ListSharedCoursesHandler;
import college.b.server.handler.LoginHandler;
import college.b.server.handler.AskCourseInfoHandler;
import college.b.server.handler.WithdrawLocalHandler;
import college.b.service.AuthService;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class CollegeBServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final CommandRouter router;
  private volatile boolean running = true;

  public CollegeBServer(int port, CommandRouter router) throws IOException {
    this.router = router;
    this.serverSocket = new ServerSocket(port);
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }

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
    int port = Integer.parseInt(System.getProperty("port", "9002"));
    DataSource ds = JdbcFactory.fromClasspath("/db.properties");
    var config = new CollegeServerConfig("B", "BC", "BS");
    var accountDao = new AccountDao(ds);
    var courseDao = new CourseDao(ds);
    var choiceDao = new ChoiceDao(ds);

    var router = new CommandRouter()
        .register(Command.LOGIN, new LoginHandler(new AuthService(accountDao)))
        .register(Command.LIST_LOCAL_COURSES, new ListLocalCoursesHandler(courseDao))
        .register(Command.ENROLL, new EnrollLocalHandler(courseDao, choiceDao, config))
        .register(Command.WITHDRAW, new WithdrawLocalHandler(choiceDao))
        .register(Command.ASK_COURSE_INFO, new AskCourseInfoHandler(courseDao))
        .register(Command.LIST_SHARED_COURSES,
            new ListSharedCoursesHandler(config.integrationHost, config.integrationPort, "B", "/xsl/AtoB.xsl"))
        .register(Command.APPLY_CHOICE, new ApplyChoiceHandler(courseDao, choiceDao));

    try (var srv = new CollegeBServer(port, router)) {
      System.out.println("College B server listening on " + srv.getPort());
      srv.serve();
    }
  }
}
