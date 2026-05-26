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
  private final JLabel statusLabel;
  private final JButton refreshButton;

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
    setSize(700, 400);
    setLocationRelativeTo(null);

    var mainPanel = new JPanel(new BorderLayout());

    // Table
    tableModel = new DefaultTableModel(COLUMNS, 0) {
      @Override
      public boolean isCellEditable(int row, int col) {
        return false;
      }
    };
    var table = new JTable(tableModel);
    var scrollPane = new JScrollPane(table);
    mainPanel.add(scrollPane, BorderLayout.CENTER);

    // Bottom panel with refresh button and status label
    var bottomPanel = new JPanel(new BorderLayout());
    refreshButton = new JButton("刷新本院课程");
    refreshButton.addActionListener(e -> loadCourses());
    bottomPanel.add(refreshButton, BorderLayout.CENTER);

    statusLabel = new JLabel(" ", SwingConstants.CENTER);
    bottomPanel.add(statusLabel, BorderLayout.SOUTH);

    mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    add(mainPanel);

    // Initial load
    loadCourses();
  }

  private void loadCourses() {
    statusLabel.setText("正在加载...");
    refreshButton.setEnabled(false);

    new Thread(() -> {
      try {
        Message req = new Message(Command.LIST_LOCAL_COURSES,
            UUID.randomUUID().toString(), "");
        Message res = client.send(req);

        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            populateTable(res.payload());
            statusLabel.setText("加载完成");
          } else if (res.command() == Command.ERR) {
            String detail = parseErrorDetail(res.payload());
            statusLabel.setText("加载失败: " + detail);
            JOptionPane.showMessageDialog(CourseListFrame.this,
                "加载课程失败: " + detail, "错误", JOptionPane.ERROR_MESSAGE);
          } else {
            statusLabel.setText("加载失败: 未知响应");
          }
          refreshButton.setEnabled(true);
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> {
          statusLabel.setText("网络错误: " + e.getMessage());
          refreshButton.setEnabled(true);
          JOptionPane.showMessageDialog(CourseListFrame.this,
              "网络错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        });
      }
    }).start();
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
}
