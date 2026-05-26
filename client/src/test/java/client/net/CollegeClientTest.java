package client.net;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class CollegeClientTest {

  @Test
  void send_receivesEchoedResponse() throws Exception {
    try (var serverSocket = new ServerSocket(0)) {
      // This server echoes back a fixed OK response to any request.
      Thread serverThread = new Thread(() -> {
        try {
          try (Socket sock = serverSocket.accept()) {
            // Read the incoming request (discard it).
            Message.read(sock.getInputStream());
            // Send back an OK response.
            Message.write(sock.getOutputStream(),
                new Message(Command.OK, "r1", "<session><role>student</role></session>"));
          }
        } catch (Exception ignored) { /* shutdown */ }
      });
      serverThread.start();

      int port = serverSocket.getLocalPort();
      CollegeClient client = new CollegeClient("localhost", port);

      Message req = new Message(Command.LOGIN, "r1",
          "<login><user>u</user><pass>p</pass></login>");
      Message res = client.send(req);

      assertEquals(Command.OK, res.command());
      assertEquals("r1", res.requestId());
      assertTrue(res.payload().contains("<role>student</role>"));

      serverThread.join(2000);
    }
  }

  @Test
  void send_receivesErrorResponse() throws Exception {
    try (var serverSocket = new ServerSocket(0)) {
      Thread serverThread = new Thread(() -> {
        try {
          try (Socket sock = serverSocket.accept()) {
            // Read the incoming request.
            Message.read(sock.getInputStream());
            // Send back an ERR response.
            Message.write(sock.getOutputStream(),
                Message.err("r2", "AUTH_FAILED", "invalid credentials"));
          }
        } catch (Exception ignored) { /* shutdown */ }
      });
      serverThread.start();

      int port = serverSocket.getLocalPort();
      CollegeClient client = new CollegeClient("localhost", port);

      Message req = new Message(Command.LOGIN, "r2",
          "<login><user>x</user><pass>y</pass></login>");
      Message res = client.send(req);

      assertEquals(Command.ERR, res.command());
      assertEquals("r2", res.requestId());
      assertTrue(res.payload().contains("AUTH_FAILED"));

      serverThread.join(2000);
    }
  }

  @Test
  void send_readsBackWhatServerWrites() throws Exception {
    try (var serverSocket = new ServerSocket(0)) {
      Thread serverThread = new Thread(() -> {
        try {
          try (Socket sock = serverSocket.accept()) {
            // Read the incoming request and echo it back.
            Message req = Message.read(sock.getInputStream());
            Message.write(sock.getOutputStream(), req);
          }
        } catch (Exception ignored) { /* shutdown */ }
      });
      serverThread.start();

      int port = serverSocket.getLocalPort();
      CollegeClient client = new CollegeClient("localhost", port);

      Message req = new Message(Command.LIST_LOCAL_COURSES, "r3", "");
      Message res = client.send(req);

      assertEquals(Command.LIST_LOCAL_COURSES, res.command());
      assertEquals("r3", res.requestId());

      serverThread.join(2000);
    }
  }
}
