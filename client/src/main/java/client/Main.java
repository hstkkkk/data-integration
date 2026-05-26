package client;

import client.net.CollegeClient;
import client.ui.LoginFrame;

import javax.swing.*;

public class Main {

  public static void main(String[] args) {
    String rawCollege = null;
    String rawServer = null;

    for (String arg : args) {
      if (arg.startsWith("--")) {
        String kv = arg.substring(2);
        int eq = kv.indexOf('=');
        if (eq < 0) continue;
        String key = kv.substring(0, eq);
        String value = kv.substring(eq + 1);
        if ("college".equals(key)) {
          rawCollege = value;
        } else if ("server".equals(key)) {
          rawServer = value;
        }
      }
    }

    if (rawCollege == null || rawServer == null) {
      System.err.println("Usage: --college=A --server=host:port");
      System.exit(1);
    }

    final String college = rawCollege;
    final String server = rawServer;

    String[] parts = server.split(":");
    if (parts.length != 2) {
      System.err.println("Invalid server format, expected host:port");
      System.exit(1);
    }

    String host = parts[0];
    int parsedPort;
    try {
      parsedPort = Integer.parseInt(parts[1]);
    } catch (NumberFormatException e) {
      System.err.println("Invalid port: " + parts[1]);
      System.exit(1);
      return;
    }
    final int port = parsedPort;

    final CollegeClient client = new CollegeClient(host, port);

    SwingUtilities.invokeLater(() -> {
      LoginFrame frame = new LoginFrame(college, client);
      frame.setVisible(true);
    });
  }
}
