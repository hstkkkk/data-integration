package analytics.ui;

import analytics.model.MonitorSnapshot;
import analytics.monitor.MonitorPoller;
import analytics.model.StatsData;
import analytics.pdf.PdfReportGenerator;
import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import cn.edu.di.xml.XmlIO;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.UUID;
import java.util.function.Function;

public class DashboardDialog extends JDialog {

  private final DashboardPanel dashboardPanel;
  private final MonitorPoller poller;
  private final Function<Message, Message> sender; // inject network layer

  public DashboardDialog(Frame owner,
                         Function<Message, Message> sender,
                         String integrationHost, int integrationPort) {
    super(owner, "集成分析中心", false);
    this.sender = sender;
    this.dashboardPanel = new DashboardPanel();
    this.poller = new MonitorPoller(integrationHost, integrationPort, dashboardPanel::updateMonitor);

    setLayout(new BorderLayout());
    add(dashboardPanel, BorderLayout.CENTER);

    var buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
    var refreshBtn = new JButton("刷新图表");
    var exportBtn = new JButton("导出 PDF");
    var closeBtn = new JButton("关闭");
    refreshBtn.addActionListener(e -> loadAnalyticsData());
    exportBtn.addActionListener(e -> exportPdf());
    closeBtn.addActionListener(e -> dispose());
    buttonPanel.add(refreshBtn);
    buttonPanel.add(exportBtn);
    buttonPanel.add(closeBtn);
    add(buttonPanel, BorderLayout.SOUTH);

    setSize(900, 700);
    setLocationRelativeTo(owner);
  }

  @Override public void setVisible(boolean v) {
    if (v) {
      loadAnalyticsData();
      poller.start(5000); // 5s interval
    } else {
      poller.stop();
    }
    super.setVisible(v);
  }

  private void loadAnalyticsData() {
    new Thread(() -> {
      try {
        Message resp = sender.apply(new Message(Command.ANALYTICS_EXPORT,
            UUID.randomUUID().toString(), ""));
        if (resp.command() == Command.OK) {
          var data = StatsData.fromXml(XmlIO.parse(resp.payload()));
          SwingUtilities.invokeLater(() -> dashboardPanel.showStats(data));
        }
      } catch (Exception e) {
        SwingUtilities.invokeLater(() ->
            dashboardPanel.statusLabel.setText("加载失败: " + e.getMessage()));
      }
    }).start();
  }

  private void exportPdf() {
    var data = dashboardPanel.getCurrentData();
    if (data == null) {
      JOptionPane.showMessageDialog(this, "请先加载数据", "提示", JOptionPane.WARNING_MESSAGE);
      return;
    }
    var chooser = new JFileChooser();
    chooser.setSelectedFile(new File("analytics-report.pdf"));
    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      try {
        PdfReportGenerator.saveToFile(data, chooser.getSelectedFile());
        JOptionPane.showMessageDialog(this, "PDF 已保存到 " + chooser.getSelectedFile().getAbsolutePath());
      } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "PDF 生成失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  @Override public void dispose() {
    poller.stop();
    super.dispose();
  }
}
