package college.a.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import college.a.dao.AccountDao;
import college.a.dao.ChoiceDao;
import college.a.dao.CourseDao;
import college.a.dao.StudentDao;
import college.a.jdbc.JdbcFactory;
import college.a.server.handler.ApplyChoiceHandler;
import college.a.server.handler.AskMyChoicesHandler;
import college.a.server.handler.EnrollLocalHandler;
import college.a.server.handler.ListMyChoicesHandler;
import college.a.server.handler.ListLocalCoursesHandler;
import college.a.server.handler.ListSharedCoursesHandler;
import college.a.server.handler.LoginHandler;
import college.a.server.handler.AskCourseInfoHandler;
import college.a.server.handler.GetStudentProfileHandler;
import college.a.server.handler.ListChoicesHandler;
import college.a.server.handler.RevokeChoiceHandler;
import college.a.server.handler.ListStudentsHandler;
import college.a.server.handler.HeartbeatHandler;
import college.a.server.handler.StatsForwardHandler;
import college.a.server.handler.StatsPullHandler;
import college.a.server.handler.UpdateStudentProfileHandler;
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
    var config = new CollegeServerConfig("A", "AC", "AS");
    AccountDao accountDao = new AccountDao(ds);
    CourseDao courseDao = new CourseDao(ds);
    ChoiceDao choiceDao = new ChoiceDao(ds);
    StudentDao studentDao = new StudentDao(ds);
    AuthService auth = new AuthService(accountDao);
    var heart = new HeartbeatHandler("A");
    CommandRouter router = new CommandRouter()
        .register(Command.LOGIN, new LoginHandler(auth))
        .register(Command.LIST_LOCAL_COURSES, new ListLocalCoursesHandler(courseDao))
        .register(Command.ENROLL, new EnrollLocalHandler(courseDao, choiceDao, config))
        .register(Command.WITHDRAW, new WithdrawLocalHandler(choiceDao, config))
        .register(Command.ASK_COURSE_INFO, new AskCourseInfoHandler(courseDao))
        .register(Command.LIST_SHARED_COURSES,
            new ListSharedCoursesHandler(config.integrationHost, config.integrationPort, "A", "/xsl/unifiedToA.xsl", courseDao))
        .register(Command.APPLY_CHOICE, new ApplyChoiceHandler(courseDao, choiceDao))
        .register(Command.REVOKE_CHOICE, new RevokeChoiceHandler(choiceDao))
        .register(Command.STATS_GLOBAL, new StatsForwardHandler(config))
        .register(Command.STATS_PULL, new StatsPullHandler(studentDao, courseDao, choiceDao, config))
        .register(Command.LIST_MY_CHOICES, new ListMyChoicesHandler(choiceDao, courseDao, config))
        .register(Command.ASK_MY_CHOICES, new AskMyChoicesHandler(choiceDao, courseDao))
        .register(Command.GET_STUDENT_PROFILE, new GetStudentProfileHandler(studentDao))
        .register(Command.UPDATE_STUDENT_PROFILE, new UpdateStudentProfileHandler(studentDao))
        .register(Command.LIST_STUDENTS, new ListStudentsHandler(studentDao))
        .register(Command.LIST_CHOICES, new ListChoicesHandler(choiceDao))
        .register(Command.HEARTBEAT, heart);
    CollegeAServer server = new CollegeAServer(port, router);
    server.serve();
  }
}
