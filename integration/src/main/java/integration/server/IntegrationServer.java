package integration.server;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import integration.server.handler.CrossEnrollHandler;
import integration.server.handler.CrossWithdrawHandler;
import integration.server.handler.FetchSharedCoursesHandler;
import integration.server.handler.PingHandler;
import integration.server.handler.StatsGlobalHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class IntegrationServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final IntegrationRouter router;
  private volatile boolean running = true;

  public IntegrationServer(int port, IntegrationRouter router) throws IOException {
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
    int port = Integer.parseInt(System.getProperty("port", "9100"));
    var clientA = new CollegeClient("127.0.0.1", 9001);
    var clientB = new CollegeClient("127.0.0.1", 9002);
    var clientC = new CollegeClient("127.0.0.1", 9003);
    IntegrationRouter router = new IntegrationRouter()
        .register(Command.PING, new PingHandler())
        .register(Command.FETCH_SHARED_COURSES, new FetchSharedCoursesHandler(clientA, clientB, clientC))
        .register(Command.CROSS_ENROLL, new CrossEnrollHandler(clientA, clientB, clientC))
        .register(Command.CROSS_WITHDRAW, new CrossWithdrawHandler(clientA, clientB, clientC))
        .register(Command.STATS_GLOBAL, new StatsGlobalHandler(clientA, clientB, clientC));
    IntegrationServer server = new IntegrationServer(port, router);
    server.serve();
  }
}
