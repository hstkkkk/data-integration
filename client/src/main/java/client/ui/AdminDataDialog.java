package client.ui;

import client.net.CollegeClient;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

public class AdminDataDialog extends JDialog {
  private final String college;
  private final CollegeClient client;
  private final JLabel statusLabel = new JLabel(" ");
  private final DefaultTableModel courseModel =
      new DefaultTableModel(new String[]{"课程编号", "课程名称", "课时", "学分", "教师", "地点", "共享"}, 0);
  private final DefaultTableModel studentModel =
      new DefaultTableModel(new String[]{"学号", "姓名", "性别", "院系/专业"}, 0);
  private final DefaultTableModel choiceModel =
      new DefaultTableModel(new String[]{"课程编号", "学号", "成绩"}, 0);

  public AdminDataDialog(Frame owner, String college, CollegeClient client) {
    super(owner, "教务数据管理", true);
    this.college = college;
    this.client = client;
    setSize(760, 480);
    setLocationRelativeTo(owner);
    setLayout(new BorderLayout());

    var tabs = new JTabbedPane();
    tabs.addTab("课程", tablePanel(courseModel, () -> load(Command.LIST_LOCAL_COURSES, courseModel)));
    tabs.addTab("学生", tablePanel(studentModel, () -> load(Command.LIST_STUDENTS, studentModel)));
    tabs.addTab("选课", tablePanel(choiceModel, () -> load(Command.LIST_CHOICES, choiceModel)));
    add(tabs, BorderLayout.CENTER);
    statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
    add(statusLabel, BorderLayout.SOUTH);
    load(Command.LIST_LOCAL_COURSES, courseModel);
  }

  private JPanel tablePanel(DefaultTableModel model, Runnable refreshAction) {
    var panel = new JPanel(new BorderLayout());
    var table = new JTable(model);
    table.setDefaultEditor(Object.class, null);
    var button = new JButton("刷新");
    button.addActionListener(e -> refreshAction.run());
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    panel.add(button, BorderLayout.SOUTH);
    return panel;
  }

  private void load(Command command, DefaultTableModel model) {
    statusLabel.setText("正在加载...");
    new Thread(() -> {
      try {
        var res = client.send(new Message(command, UUID.randomUUID().toString(), ""));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            populate(command, model, res.payload());
            statusLabel.setText("加载完成");
          } else {
            statusLabel.setText("加载失败: " + parseErrorDetail(res.payload()));
          }
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("网络错误: " + e.getMessage()));
      }
    }).start();
  }

  private void populate(Command command, DefaultTableModel model, String xml) {
    try {
      model.setRowCount(0);
      var root = XmlIO.parse(xml).getRootElement();
      if (command == Command.LIST_LOCAL_COURSES) {
        boolean isC = "courses".equals(root.getName());
        for (Object o : root.elements(isC ? "course" : "课程")) {
          var e = (org.dom4j.Element) o;
          if (isC) model.addRow(new Object[]{e.elementText("Cno"), e.elementText("Cnm"),
              e.elementText("Ctm"), e.elementText("Cpt"), e.elementText("Tec"),
              e.elementText("Pla"), e.elementText("Share")});
          else if ("B".equals(college)) model.addRow(new Object[]{e.elementText("编号"),
              e.elementText("名称"), e.elementText("课时"), e.elementText("学分"),
              e.elementText("老师"), e.elementText("地点"), e.elementText("共享")});
          else model.addRow(new Object[]{e.elementText("课程编号"), e.elementText("课程名称"),
              e.elementText("课时"), e.elementText("学分"), e.elementText("授课老师"),
              e.elementText("授课地点"), e.elementText("共享")});
        }
      } else if (command == Command.LIST_STUDENTS) {
        boolean isC = "students".equals(root.getName());
        for (Object o : root.elements(isC ? "student" : "学生")) {
          var e = (org.dom4j.Element) o;
          if (isC) model.addRow(new Object[]{e.elementText("Sno"), e.elementText("Snm"),
              e.elementText("Sex"), e.elementText("Sde")});
          else model.addRow(new Object[]{e.elementText("学号"), e.elementText("姓名"),
              e.elementText("性别"), "B".equals(college) ? e.elementText("专业") : e.elementText("院系")});
        }
      } else {
        boolean isC = "choices".equals(root.getName());
        for (Object o : root.elements(isC ? "choice" : "选课")) {
          var e = (org.dom4j.Element) o;
          if (isC) model.addRow(new Object[]{e.elementText("Cno"), e.elementText("Sno"), e.elementText("Grd")});
          else model.addRow(new Object[]{e.elementText("课程编号"),
              "B".equals(college) ? e.elementText("学号") : e.elementText("学生编号"),
              "B".equals(college) ? e.elementText("得分") : e.elementText("成绩")});
        }
      }
    } catch (Exception e) {
      statusLabel.setText("解析失败: " + e.getMessage());
    }
  }

  private static String parseErrorDetail(String xml) {
    try {
      String detail = XmlIO.parse(xml).getRootElement().elementText("detail");
      return detail == null ? "unknown error" : detail;
    } catch (Exception e) {
      return "unknown error";
    }
  }
}
