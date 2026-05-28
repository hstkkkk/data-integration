package client.ui;

import client.net.CollegeClient;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import java.awt.*;
import java.util.UUID;

public class ProfileDialog extends JDialog {
  private final String college;
  private final String studentId;
  private final CollegeClient client;
  private final JTextField idField = new JTextField(16);
  private final JTextField nameField = new JTextField(16);
  private final JTextField sexField = new JTextField(16);
  private final JTextField majorField = new JTextField(16);
  private final JLabel statusLabel = new JLabel(" ");

  public ProfileDialog(Frame owner, String college, String studentId, CollegeClient client) {
    super(owner, "个人信息", true);
    this.college = college;
    this.studentId = studentId;
    this.client = client;

    setSize(360, 260);
    setLocationRelativeTo(owner);
    setLayout(new BorderLayout(8, 8));

    var form = new JPanel(new GridBagLayout());
    var gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 8, 5, 8);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    addRow(form, gbc, 0, "学号", idField);
    addRow(form, gbc, 1, "姓名", nameField);
    addRow(form, gbc, 2, "性别", sexField);
    addRow(form, gbc, 3, "院系/专业", majorField);
    idField.setEditable(false);

    var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    var refresh = new JButton("刷新");
    var save = new JButton("保存");
    refresh.addActionListener(e -> loadProfile());
    save.addActionListener(e -> saveProfile());
    buttons.add(refresh);
    buttons.add(save);

    statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
    add(form, BorderLayout.CENTER);
    add(buttons, BorderLayout.SOUTH);
    add(statusLabel, BorderLayout.NORTH);

    loadProfile();
  }

  private static void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field) {
    gbc.gridy = row;
    gbc.gridx = 0;
    gbc.weightx = 0;
    panel.add(new JLabel(label), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1;
    panel.add(field, gbc);
  }

  private void loadProfile() {
    statusLabel.setText("正在加载...");
    new Thread(() -> {
      try {
        var res = client.send(new Message(Command.GET_STUDENT_PROFILE,
            UUID.randomUUID().toString(), studentId));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            populate(res.payload());
            statusLabel.setText("已加载");
          } else {
            statusLabel.setText("加载失败: " + parseErrorDetail(res.payload()));
          }
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("网络错误: " + e.getMessage()));
      }
    }).start();
  }

  private void saveProfile() {
    String payload = buildPayload();
    statusLabel.setText("正在保存...");
    new Thread(() -> {
      try {
        var res = client.send(new Message(Command.UPDATE_STUDENT_PROFILE,
            UUID.randomUUID().toString(), payload));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            statusLabel.setText("保存成功");
          } else {
            statusLabel.setText("保存失败: " + parseErrorDetail(res.payload()));
          }
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("网络错误: " + e.getMessage()));
      }
    }).start();
  }

  private void populate(String xml) {
    try {
      var root = XmlIO.parse(xml).getRootElement();
      var row = (org.dom4j.Element) root.elements().get(0);
      if ("C".equals(college)) {
        idField.setText(row.elementText("Sno"));
        nameField.setText(row.elementText("Snm"));
        sexField.setText(row.elementText("Sex"));
        majorField.setText(row.elementText("Sde"));
      } else if ("B".equals(college)) {
        idField.setText(row.elementText("学号"));
        nameField.setText(row.elementText("姓名"));
        sexField.setText(row.elementText("性别"));
        majorField.setText(row.elementText("专业"));
      } else {
        idField.setText(row.elementText("学号"));
        nameField.setText(row.elementText("姓名"));
        sexField.setText(row.elementText("性别"));
        majorField.setText(row.elementText("院系"));
      }
    } catch (Exception e) {
      statusLabel.setText("解析失败: " + e.getMessage());
    }
  }

  private String buildPayload() {
    String id = esc(idField.getText());
    String name = esc(nameField.getText());
    String sex = esc(sexField.getText());
    String major = esc(majorField.getText());
    if ("C".equals(college)) {
      return "<student><Sno>" + id + "</Sno><Snm>" + name + "</Snm><Sex>" + sex
          + "</Sex><Sde>" + major + "</Sde></student>";
    }
    String majorTag = "B".equals(college) ? "专业" : "院系";
    return "<学生><学号>" + id + "</学号><姓名>" + name + "</姓名><性别>" + sex
        + "</性别><" + majorTag + ">" + major + "</" + majorTag + "></学生>";
  }

  private static String parseErrorDetail(String xml) {
    try {
      var root = XmlIO.parse(xml).getRootElement();
      String detail = root.elementText("detail");
      return detail == null ? "unknown error" : detail;
    } catch (Exception e) {
      return "unknown error";
    }
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
