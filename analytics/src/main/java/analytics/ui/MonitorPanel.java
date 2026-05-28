package analytics.ui;

import analytics.model.MonitorSnapshot;
import javax.swing.*;
import java.awt.*;

public class MonitorPanel extends JPanel {

  private final JLabel indicatorA = createIndicator();
  private final JLabel indicatorB = createIndicator();
  private final JLabel indicatorC = createIndicator();
  private final JLabel indicatorI = createIndicator();
  private final JLabel infoLabel = new JLabel("等待首次探测...");

  public MonitorPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("实时监控"));
    add(createRow("Integration Server", indicatorI));
    add(createRow("College A", indicatorA));
    add(createRow("College B", indicatorB));
    add(createRow("College C", indicatorC));
    add(infoLabel);
  }

  public void update(MonitorSnapshot snapshot) {
    SwingUtilities.invokeLater(() -> {
      for (var s : snapshot.servers()) {
        JLabel indicator = switch (s.name()) {
          case "A" -> indicatorA;
          case "B" -> indicatorB;
          case "C" -> indicatorC;
          default -> indicatorI;
        };
        indicator.setForeground(s.online() ? Color.GREEN : Color.RED);
      }
      infoLabel.setText("最后更新: " + snapshot.timestamp());
    });
  }

  private JPanel createRow(String label, JLabel indicator) {
    var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    row.add(indicator);
    row.add(new JLabel(label));
    return row;
  }

  private static JLabel createIndicator() {
    var l = new JLabel("●");
    l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
    l.setForeground(Color.GRAY);
    return l;
  }
}
