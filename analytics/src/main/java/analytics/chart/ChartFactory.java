package analytics.chart;

import analytics.model.StatsData;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

public final class ChartFactory {

  private ChartFactory() {}

  /** Three-college comparison bar chart: students, courses, shared, cross per college */
  public static JFreeChart createComparisonBarChart(StatsData data) {
    var dataset = new DefaultCategoryDataset();
    for (var c : data.colleges()) {
      dataset.addValue(c.students(), "学生数", c.code());
      dataset.addValue(c.courses(), "课程数", c.code());
      dataset.addValue(c.shared(), "共享课程", c.code());
      dataset.addValue(c.cross(), "跨院选课", c.code());
    }
    return org.jfree.chart.ChartFactory.createBarChart(
        "三院数据对比", "学院", "数量", dataset,
        PlotOrientation.VERTICAL, true, true, false);
  }

  /** Pie chart showing student count distribution across colleges */
  public static JFreeChart createEnrollmentPieChart(StatsData data) {
    var dataset = new DefaultPieDataset<String>();
    for (var c : data.colleges()) {
      dataset.setValue("学院 " + c.code(), c.students());
    }
    return org.jfree.chart.ChartFactory.createPieChart(
        "三院学生数占比", dataset, true, true, false);
  }

  /** Horizontal bar chart of top courses by enrollment count */
  public static JFreeChart createTopCoursesChart(StatsData data) {
    var dataset = new DefaultCategoryDataset();
    for (var e : data.topCourses()) {
      dataset.addValue(e.enrollments(), "选课人数", e.name() + " (" + e.origin() + ")");
    }
    return org.jfree.chart.ChartFactory.createBarChart(
        "课程热度 Top " + data.topCourses().size(), "课程", "选课人数", dataset,
        PlotOrientation.HORIZONTAL, false, true, false);
  }
}
