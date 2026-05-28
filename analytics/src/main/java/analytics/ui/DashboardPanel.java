package analytics.ui;

import analytics.chart.ChartFactory;
import analytics.model.MonitorSnapshot;
import analytics.model.StatsData;
import org.jfree.chart.ChartPanel;

import javax.swing.*;
import java.awt.*;

public class DashboardPanel extends JPanel {

  private final JPanel chartGrid = new JPanel(new GridLayout(1, 2, 8, 8));
  private final MonitorPanel monitorPanel = new MonitorPanel();
  public final JLabel statusLabel = new JLabel(" ");

  private StatsData currentData;

  public DashboardPanel() {
    setLayout(new BorderLayout());
    add(new JScrollPane(chartGrid), BorderLayout.CENTER);
    var bottom = new JPanel(new BorderLayout());
    bottom.add(monitorPanel, BorderLayout.CENTER);
    bottom.add(statusLabel, BorderLayout.SOUTH);
    add(bottom, BorderLayout.SOUTH);
  }

  public void showStats(StatsData data) {
    this.currentData = data;
    chartGrid.removeAll();
    chartGrid.add(new ChartPanel(ChartFactory.createComparisonBarChart(data)));
    chartGrid.add(new ChartPanel(ChartFactory.createEnrollmentPieChart(data)));
    chartGrid.revalidate();
    chartGrid.repaint();
    statusLabel.setText("数据加载完成");
  }

  public void updateMonitor(MonitorSnapshot snapshot) {
    monitorPanel.update(snapshot);
  }

  public StatsData getCurrentData() { return currentData; }
}
