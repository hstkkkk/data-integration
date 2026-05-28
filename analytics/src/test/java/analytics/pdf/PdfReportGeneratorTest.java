package analytics.pdf;

import analytics.model.StatsData;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PdfReportGeneratorTest {
  @Test void generate_returns_non_empty_bytes() throws Exception {
    var s = new StatsData.Summary(150, 30, 12, 45);
    var c = new StatsData.CollegeStat("A", 50, 10, 4, 15);
    var top = new StatsData.CourseEntry("AC001", "DB", 28, "A");
    var data = new StatsData(s, List.of(c), List.of(top));
    byte[] pdf = PdfReportGenerator.generate(data);
    assertTrue(pdf.length > 0);
    // PDF magic bytes
    assertEquals('%', pdf[0]);
    assertEquals('P', pdf[1]);
    assertEquals('D', pdf[2]);
    assertEquals('F', pdf[3]);
  }

  @Test void empty_data_generates_valid_pdf() throws Exception {
    var data = new StatsData(new StatsData.Summary(0, 0, 0, 0),
        Collections.emptyList(), Collections.emptyList());
    byte[] pdf = PdfReportGenerator.generate(data);
    assertTrue(pdf.length > 0);
    assertEquals('%', pdf[0]);
  }
}
