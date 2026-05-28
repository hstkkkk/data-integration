package client.ui;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;
import client.net.CollegeClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;

public class MyChoicesDialog extends JDialog {

  private static final String[] COLS_HOME =
      {"课程编号/编号/Cno", "课程名称", "学分", "授课老师", "授课地点", "成绩"};
  private static final String[] COLS_CROSS =
      {"来自学院", "课程编号/编号/Cno", "课程名称", "学分", "授课老师", "授课地点", "成绩"};

  public MyChoicesDialog(JFrame parent, String college, String sno, CollegeClient client) {
    super(parent, "我的选课 - " + sno, true);
    setSize(820, 520);
    setLocationRelativeTo(parent);

    var status = new JLabel("正在加载...", SwingConstants.CENTER);
    var homeModel = new DefaultTableModel(COLS_HOME, 0) {
      @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    var crossModel = new DefaultTableModel(COLS_CROSS, 0) {
      @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
        wrap("本院选课", new JTable(homeModel)),
        wrap("跨院选课", new JTable(crossModel)));
    split.setResizeWeight(0.5);

    var panel = new JPanel(new BorderLayout());
    panel.add(split, BorderLayout.CENTER);
    panel.add(status, BorderLayout.SOUTH);
    add(panel);

    new Thread(() -> {
      try {
        Message res = client.send(new Message(Command.LIST_MY_CHOICES,
            UUID.randomUUID().toString(), "<sno>" + esc(sno) + "</sno>"));
        SwingUtilities.invokeLater(() -> {
          if (res.command() == Command.OK) {
            populate(college, res.payload(), homeModel, crossModel, status);
          } else {
            status.setText("加载失败: " + res.payload());
          }
        });
      } catch (Exception e) {
        SwingUtilities.invokeLater(() -> status.setText("网络错误: " + e.getMessage()));
      }
    }).start();
  }

  private static JComponent wrap(String title, JTable table) {
    var sp = new JScrollPane(table);
    sp.setBorder(BorderFactory.createTitledBorder(title));
    return sp;
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static void populate(String college, String xml,
                               DefaultTableModel homeModel, DefaultTableModel crossModel,
                               JLabel status) {
    try {
      var root = XmlIO.parse(xml).getRootElement();
      // home block
      var homeEl = root.element("home");
      if (homeEl != null) {
        if ("C".equals(college)) {
          var courses = homeEl.element("courses");
          if (courses != null) {
            for (Object o : courses.elements("course")) {
              var e = (org.dom4j.Element) o;
              homeModel.addRow(new Object[]{
                  e.elementText("Cno"), e.elementText("Cnm"),
                  e.elementText("Cpt"), e.elementText("Tec"),
                  e.elementText("Pla"), e.elementText("Grd")
              });
            }
          }
        } else {
          var courseSet = homeEl.element("课程集");
          if (courseSet != null) {
            for (Object o : courseSet.elements("课程")) {
              var e = (org.dom4j.Element) o;
              boolean isB = "B".equals(college);
              homeModel.addRow(new Object[]{
                  e.elementText(isB ? "编号" : "课程编号"),
                  e.elementText(isB ? "名称" : "课程名称"),
                  e.elementText("学分"),
                  e.elementText(isB ? "老师" : "授课老师"),
                  e.elementText(isB ? "地点" : "授课地点"),
                  e.elementText(isB ? "得分" : "成绩")
              });
            }
          }
        }
      }
      // cross block: home college server already translated <crossEnrolled> into local fields
      var crossEl = root.element("crossEnrolled");
      if (crossEl != null) {
        if ("C".equals(college)) {
          for (Object o : crossEl.elements("course")) {
            var e = (org.dom4j.Element) o;
            crossModel.addRow(new Object[]{
                e.elementText("Org"),
                e.elementText("Cno"), e.elementText("Cnm"),
                e.elementText("Cpt"), e.elementText("Tec"),
                e.elementText("Pla"), e.elementText("Grd")
            });
          }
        } else {
          boolean isB = "B".equals(college);
          for (Object o : crossEl.elements("课程")) {
            var e = (org.dom4j.Element) o;
            crossModel.addRow(new Object[]{
                e.elementText("来源"),
                e.elementText(isB ? "编号" : "课程编号"),
                e.elementText(isB ? "名称" : "课程名称"),
                e.elementText("学分"),
                e.elementText(isB ? "老师" : "授课老师"),
                e.elementText(isB ? "地点" : "授课地点"),
                e.elementText(isB ? "得分" : "成绩")
            });
          }
        }
      }
      // errors
      var errors = root.element("errors");
      if (errors != null && !errors.elements().isEmpty()) {
        var sb = new StringBuilder("外院故障: ");
        for (Object o : errors.elements("error")) {
          var e = (org.dom4j.Element) o;
          sb.append("[").append(e.attributeValue("college")).append("] ")
            .append(e.getText()).append("  ");
        }
        status.setText(sb.toString());
        status.setForeground(Color.RED);
      } else {
        int total = homeModel.getRowCount() + crossModel.getRowCount();
        status.setText(total == 0 ? "该学生无任何选课记录" : "加载完成 (共 " + total + " 门)");
      }
    } catch (Exception e) {
      status.setText("解析失败: " + e.getMessage());
      status.setForeground(Color.RED);
    }
  }
}
