package college.a.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.dao.AccountDao;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.jdbc.JdbcFactory;
import college.a.server.handler.EnrollLocalHandler;
import college.a.server.handler.ListLocalCoursesHandler;
import college.a.server.handler.LoginHandler;
import college.a.server.handler.WithdrawLocalHandler;
import college.a.service.AuthService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import javax.sql.DataSource;

public class CollegeAServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final CommandRouter router;
  private volatile boolean running = true;

  public CollegeAServer(int port, CommandRouter router) throws IOException {
    this.router = router;
    this.serverSocket = new ServerSocket(port);
  }

  /** Returns the port this server is actually listening on. */
  public int getPort() {
    return serverSocket.getLocalPort();
  }

  /** Start the accept loop, blocking the calling thread. */
  public void serve() {
    while (running) {
      try {
        Socket client = serverSocket.accept();
        new Thread(() -> handleClient(client)).start();
      } catch (SocketException e) {
        if (!running) break; // expected during shutdown
        System.err.println("accept error: " + e.getMessage());
      } catch (IOException e) {
        if (!running) break;
        System.err.println("accept error: " + e.getMessage());
      }
    }
  }

  private void handleClient(Socket client) {
    try (client) {
      Message req = Message.read(client.getInputStream());
      Message res = router.dispatch(req);
      Message.write(client.getOutputStream(), res);
    } catch (Exception e) {
      System.err.println("client error: " + e.getMessage());
    }
  }

  @Override
  public void close() throws IOException {
    running = false;
    serverSocket.close();
  }

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getProperty("port", "9001"));
    DataSource ds = JdbcFactory.fromClasspath("/db.properties");
    AccountDao accountDao = new AccountDao(ds);
    CourseDao courseDao = new CourseDao(ds);
    ChoiceDao choiceDao = new ChoiceDao(ds);
    AuthService auth = new AuthService(accountDao);
    CommandRouter router = new CommandRouter()
        .register(Command.LOGIN, new LoginHandler(auth))
        .register(Command.LIST_LOCAL_COURSES, new ListLocalCoursesHandler(courseDao))
        .register(Command.ENROLL, new EnrollLocalHandler(courseDao, choiceDao))
        .register(Command.WITHDRAW, new WithdrawLocalHandler(choiceDao));
    CollegeAServer server = new CollegeAServer(port, router);
    server.serve();
  }
}
