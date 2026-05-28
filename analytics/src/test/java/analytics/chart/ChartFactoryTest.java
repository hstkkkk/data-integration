package analytics.chart;

import analytics.model.StatsData;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChartFactoryTest {
  private final StatsData.Summary summary = new StatsData.Summary(150, 30, 12, 45);
  private final StatsData.CollegeStat a = new StatsData.CollegeStat("A", 50, 10, 4, 15);
  private final StatsData.CollegeStat b = new StatsData.CollegeStat("B", 50, 10, 4, 18);
  private final StatsData.CollegeStat c = new StatsData.CollegeStat("C", 50, 10, 4, 12);
  private final StatsData.CourseEntry top1 = new StatsData.CourseEntry("BC001", "数据库原理", 28, "B");
  private final StatsData stats = new StatsData(summary, List.of(a, b, c), List.of(top1));

  @Test void comparisonBarChart_notNull() {
    assertNotNull(ChartFactory.createComparisonBarChart(stats));
  }

  @Test void enrollmentPieChart_notNull() {
    assertNotNull(ChartFactory.createEnrollmentPieChart(stats));
  }

  @Test void topCoursesChart_notNull() {
    assertNotNull(ChartFactory.createTopCoursesChart(stats));
  }

  @Test void emptyStats_producesChartsWithoutException() {
    var empty = new StatsData(new StatsData.Summary(0, 0, 0, 0),
        Collections.emptyList(), Collections.emptyList());
    assertNotNull(ChartFactory.createComparisonBarChart(empty));
    assertNotNull(ChartFactory.createEnrollmentPieChart(empty));
  }
}
