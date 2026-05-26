package client.net;

import cn.edu.di.protocol.Message;

import java.io.IOException;
import java.net.Socket;

public class CollegeClient {
  private final String host;
  private final int port;

  public CollegeClient(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public Message send(Message req) throws IOException {
    try (var sock = new Socket(host, port)) {
      Message.write(sock.getOutputStream(), req);
      return Message.read(sock.getInputStream());
    }
  }
}
