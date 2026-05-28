package client.ui;

import client.net.CollegeClient;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

public class CourseListFrame extends JFrame {

  private final String college;
  private final String username;
  private final String role;
  private final CollegeClient client;
  private final DefaultTableModel tableModel;
  private final JTable table;
  private final JLabel statusLabel;
  private final JButton refreshLocalButton;
  private final JButton refreshSharedButton;
  private final JButton enrollButton;
  private final JButton withdrawButton;
  private final JButton statsButton;
  private final JButton myChoicesButton;

  private static final String[] COLUMNS = {
      "课程编号", "课程名称", "学分", "授课老师", "授课地点", "共享"
  };

  public CourseListFrame(String college, String username, String role, CollegeClient client) {
    this.college = college;
    this.username = username;
    this.role = role;
    this.client = client;

    setTitle("学院 " + college + "  欢迎 " + username + "（" + role + "）");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(820, 460);
    setLocationRelativeTo(null);

    var mainPanel = new JPanel(new BorderLayout());

    tableModel = new DefaultTableModel(COLUMNS, 0) {
      @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    table = new JTable(tableModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);

    var buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
    refreshLocalButton = new JButton("刷新本院课程");
    refreshSharedButton = new JButton("刷新共享课程");
    enrollButton = new JButton("选课");
    withdrawButton = new JButton("退课");
    statsButton = new JButton("全局统计");
    myChoicesButton = new JButton("我的选课");
    myChoicesButton.addActionListener(e -> openMyChoices());
    refreshLocalButton.addActionListener(e -> loadLocalCourses());
    refreshSharedButton.addActionListener(e -> loadSharedCourses());
    enrollButton.addActionListener(e -> doEnroll());
    withdrawButton.addActionListener(e -> doWithdraw());
    statsButton.addActionListener(e -> doStats());
    buttonPanel.add(refreshLocalButton);
    buttonPanel.add(refreshSharedButton);
    buttonPanel.add(enrollButton);
    buttonPanel.add(withdrawButton);
    buttonPanel.add(statsButton);
    buttonPanel.add(myChoicesButton);

    statusLabel = new JLabel(" ", SwingConstants.CENTER);

    var bottomPanel = new JPanel(new BorderLayout());
    bottomPanel.add(buttonPanel, BorderLayout.CENTER);
    bottomPanel.add(statusLabel, BorderLayout.SOUTH);
    mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    add(mainPanel);

    loadLocalCourses();
  }

  private void loadLocalCourses() {
    sendAndPopulate(new Message(Command.LIST_LOCAL_COURSES, UUID.randomUUID().toString(), ""),
        "本院课程");
  }

  private void loadSharedCourses() {
    sendAndPopulate(new Message(Command.LIST_SHARED_COURSES, UUID.randomUUID().toString(), ""),
        "共享课程");
  }

  private void sendAndPopulate(Message req, String label) {
    statusLabel.setText("正在加载" + label + "...");
    setButtonsEnabled(false);
    new Thread(() -> {
      try {
        Message res = client.send(req);
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            populateTable(res.payload());
            statusLabel.setText(label + "加载完成");
          } else if (res.command() == Command.ERR) {
            String detail = parseErrorDetail(res.payload());
            statusLabel.setText(label + "加载失败: " + detail);
            JOptionPane.showMessageDialog(this,
                "加载" + label + "失败: " + detail, "错误", JOptionPane.ERROR_MESSAGE);
          } else {
            statusLabel.setText(label + "加载失败: 未知响应");
          }
          setButtonsEnabled(true);
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("网络错误: " + e.getMessage());
          setButtonsEnabled(true);
          JOptionPane.showMessageDialog(this,
              "网络错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        });
      }
    }).start();
  }

  private void doEnroll() { sendChoice(Command.ENROLL, "选课"); }
  private void doWithdraw() { sendChoice(Command.WITHDRAW, "退课"); }

  private void sendChoice(Command cmd, String label) {
    int row = table.getSelectedRow();
    if (row < 0) {
      JOptionPane.showMessageDialog(this, "请先选中课程行", "提示", JOptionPane.WARNING_MESSAGE);
      return;
    }
    String courseId = String.valueOf(tableModel.getValueAt(row, 0));
    String studentId = JOptionPane.showInputDialog(this,
        label + "学生编号:", username);
    if (studentId == null || studentId.isBlank()) return;

    String payload = buildChoicePayload(courseId, studentId.trim());
    statusLabel.setText("正在" + label + "...");
    setButtonsEnabled(false);
    new Thread(() -> {
      try {
        Message res = client.send(new Message(cmd, UUID.randomUUID().toString(), payload));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            statusLabel.setText(label + "成功: " + studentId.trim() + " / " + courseId);
            JOptionPane.showMessageDialog(this, label + "成功", "提示",
                JOptionPane.INFORMATION_MESSAGE);
          } else if (res.command() == Command.ERR) {
            String detail = parseErrorDetail(res.payload());
            statusLabel.setText(label + "失败: " + detail);
            JOptionPane.showMessageDialog(this, label + "失败: " + detail, "错误",
                JOptionPane.ERROR_MESSAGE);
          } else {
            statusLabel.setText(label + "失败: 未知响应");
            JOptionPane.showMessageDialog(this, label + "失败: 未知响应", "错误",
                JOptionPane.ERROR_MESSAGE);
          }
          setButtonsEnabled(true);
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("网络错误: " + e.getMessage());
          setButtonsEnabled(true);
          JOptionPane.showMessageDialog(this,
              "网络错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        });
      }
    }).start();
  }

  private String buildChoicePayload(String courseId, String studentId) {
    String cid = esc(courseId);
    String sid = esc(studentId);
    if ("C".equals(college)) {
      return "<choice><Cno>" + cid + "</Cno><Sno>" + sid + "</Sno></choice>";
    } else if ("B".equals(college)) {
      return "<choice><课程编号>" + cid + "</课程编号><学号>" + sid + "</学号></choice>";
    } else {
      return "<choice><课程编号>" + cid + "</课程编号><学生编号>" + sid + "</学生编号></choice>";
    }
  }

  private void doStats() {
    statusLabel.setText("正在拉取全局统计...");
    setButtonsEnabled(false);
    new Thread(() -> {
      try {
        Message res = client.send(new Message(Command.STATS_GLOBAL,
            UUID.randomUUID().toString(), ""));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            statusLabel.setText("全局统计已加载");
            showStatsDialog(res.payload());
          } else {
            String detail = parseErrorDetail(res.payload());
            statusLabel.setText("统计失败: " + detail);
            JOptionPane.showMessageDialog(this, "统计失败: " + detail, "错误",
                JOptionPane.ERROR_MESSAGE);
          }
          setButtonsEnabled(true);
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("网络错误: " + e.getMessage());
          setButtonsEnabled(true);
        });
      }
    }).start();
  }

  private void showStatsDialog(String xml) {
    StringBuilder sb = new StringBuilder();
    try {
      var root = XmlIO.parse(xml).getRootElement();
      var summary = root.element("summary");
      sb.append("【全局汇总】\n");
      sb.append("  学生总数: ").append(summary.elementText("totalStudents")).append("\n");
      sb.append("  课程总数: ").append(summary.elementText("totalCourses")).append("\n");
      sb.append("  共享课程总数: ").append(summary.elementText("totalSharedCourses")).append("\n");
      sb.append("  跨院选课总数: ").append(summary.elementText("crossEnrollments")).append("\n\n");

      sb.append("【各院明细】\n");
      var byCollege = root.element("byCollege");
      for (Object o : byCollege.elements("college")) {
        var c = (org.dom4j.Element) o;
        sb.append("  学院 ").append(c.attributeValue("code"))
          .append(": 学生=").append(c.attributeValue("students"))
          .append(", 课程=").append(c.attributeValue("courses"))
          .append(", 共享=").append(c.attributeValue("shared"))
          .append(", 跨院选课=").append(c.attributeValue("crossEnrollments"))
          .append("\n");
      }
      sb.append("\n【Top 5 课程（按选课数）】\n");
      var top = root.element("topCourses");
      int rank = 1;
      for (Object o : top.elements("course")) {
        var c = (org.dom4j.Element) o;
        sb.append("  ").append(rank++).append(". ")
          .append(c.attributeValue("id")).append(" ")
          .append(c.attributeValue("name"))
          .append(" (").append(c.attributeValue("enrollments")).append(")\n");
      }
    } catch (Exception e) {
      sb.append("解析统计数据失败: ").append(e.getMessage()).append("\n\n").append(xml);
    }
    var area = new JTextArea(sb.toString(), 20, 50);
    area.setEditable(false);
    area.setFont(new Font("Monospaced", Font.PLAIN, 13));
    JOptionPane.showMessageDialog(this, new JScrollPane(area),
        "全局统计", JOptionPane.INFORMATION_MESSAGE);
  }

  private void setButtonsEnabled(boolean enabled) {
    refreshLocalButton.setEnabled(enabled);
    refreshSharedButton.setEnabled(enabled);
    enrollButton.setEnabled(enabled);
    withdrawButton.setEnabled(enabled);
    statsButton.setEnabled(enabled);
    myChoicesButton.setEnabled(enabled);
  }

  private void openMyChoices() {
    String input = JOptionPane.showInputDialog(this, "学生编号:", username);
    if (input == null || input.isBlank()) return;
    new MyChoicesDialog(this, college, input.trim(), client).setVisible(true);
  }

  private void populateTable(String xml) {
    try {
      var doc = XmlIO.parse(xml);
      tableModel.setRowCount(0);
      String rootName = doc.getRootElement().getName();
      boolean isC = "courses".equals(rootName);

      for (var obj : doc.getRootElement().elements(isC ? "course" : "课程")) {
        var el = (org.dom4j.Element) obj;
        String id, name, score, teacher, location, shared;
        if (isC) {
          id = el.elementText("Cno"); name = el.elementText("Cnm");
          score = el.elementText("Cpt"); teacher = el.elementText("Tec");
          location = el.elementText("Pla"); shared = el.elementText("Share");
        } else if ("B".equals(college)) {
          id = el.elementText("编号"); name = el.elementText("名称");
          score = el.elementText("学分"); teacher = el.elementText("老师");
          location = el.elementText("地点"); shared = el.elementText("共享");
        } else {
          id = el.elementText("课程编号"); name = el.elementText("课程名称");
          score = el.elementText("学分"); teacher = el.elementText("授课老师");
          location = el.elementText("授课地点"); shared = el.elementText("共享");
        }
        tableModel.addRow(new Object[]{id, name, score, teacher, location, shared});
      }
    } catch (Exception e) {
      statusLabel.setText("解析数据失败: " + e.getMessage());
    }
  }

  private static String parseErrorDetail(String xml) {
    try {
      var doc = XmlIO.parse(xml);
      String detail = doc.getRootElement().elementText("detail");
      return (detail != null && !detail.isEmpty()) ? detail : "unknown error";
    } catch (Exception e) {
      return "unknown error";
    }
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
