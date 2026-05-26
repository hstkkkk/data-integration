package client.ui;

import client.net.CollegeClient;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;

public class LoginFrame extends JFrame {

  private final String college;
  private final CollegeClient client;
  private final JTextField userField;
  private final JPasswordField passField;
  private final JLabel statusLabel;

  public LoginFrame(String college, CollegeClient client) {
    this.college = college;
    this.client = client;

    setTitle("学院 " + college + " 教务系统 - 登录");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(400, 250);
    setLocationRelativeTo(null);
    setResizable(false);

    var panel = new JPanel(new GridBagLayout());
    var gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 10, 5, 10);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Title label
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    var titleLabel = new JLabel("学院 " + college + " 教务系统", SwingConstants.CENTER);
    titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
    panel.add(titleLabel, gbc);

    // Account field
    gbc.gridwidth = 1;
    gbc.gridy = 1;
    gbc.gridx = 0;
    panel.add(new JLabel("账号:"), gbc);
    userField = new JTextField(15);
    gbc.gridx = 1;
    panel.add(userField, gbc);

    // Password field
    gbc.gridy = 2;
    gbc.gridx = 0;
    panel.add(new JLabel("密码:"), gbc);
    passField = new JPasswordField(15);
    gbc.gridx = 1;
    panel.add(passField, gbc);

    // Login button
    gbc.gridy = 3;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    var loginButton = new JButton("登录");
    loginButton.addActionListener(e -> doLogin());
    panel.add(loginButton, gbc);

    // Link enter key to login
    getRootPane().setDefaultButton(loginButton);

    // Status label
    gbc.gridy = 4;
    statusLabel = new JLabel("", SwingConstants.CENTER);
    statusLabel.setForeground(Color.RED);
    panel.add(statusLabel, gbc);

    add(panel);
  }

  private void doLogin() {
    String user = userField.getText().trim();
    String pass = new String(passField.getPassword());

    if (user.isEmpty() || pass.isEmpty()) {
      statusLabel.setText("请输入账号和密码");
      return;
    }

    statusLabel.setText("正在登录...");

    // Run network call in background to avoid blocking EDT
    new Thread(() -> {
      try {
        String xml = "<login><user>" + esc(user) + "</user><pass>" + esc(pass) + "</pass></login>";
        Message req = new Message(Command.LOGIN, UUID.randomUUID().toString(), xml);
        Message res = client.send(req);

        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            String role = parseRole(res.payload());
            dispose();
            CourseListFrame frame = new CourseListFrame(college, user, role, client);
            frame.setVisible(true);
          } else if (res.command() == Command.ERR) {
            String detail = parseErrorDetail(res.payload());
            statusLabel.setText("登录失败: " + detail);
          } else {
            statusLabel.setText("登录失败: 未知响应");
          }
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() ->
            statusLabel.setText("网络错误: " + e.getMessage()));
      }
    }).start();
  }

  private static String parseRole(String xml) {
    try {
      var doc = XmlIO.parse(xml);
      return doc.getRootElement().elementText("role");
    } catch (Exception e) {
      return "unknown";
    }
  }

  private static String parseErrorDetail(String xml) {
    try {
      var doc = XmlIO.parse(xml);
      String code = doc.getRootElement().elementText("code");
      String detail = doc.getRootElement().elementText("detail");
      return (detail != null && !detail.isEmpty()) ? detail : code;
    } catch (Exception e) {
      return "unknown error";
    }
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
