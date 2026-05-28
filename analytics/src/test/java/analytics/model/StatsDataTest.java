package analytics.model;

import cn.edu.di.xml.XmlIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatsDataTest {
  @Test void parses_analytics_xml() {
    String xml = """
        <analyticsReport timestamp="2026-05-28T10:00:00">
          <summary>
            <totalStudents>150</totalStudents>
            <totalCourses>30</totalCourses>
            <totalShared>12</totalShared>
            <totalCross>45</totalCross>
          </summary>
          <colleges>
            <college code="A"><students>50</students><courses>10</courses><shared>4</shared><cross>15</cross></college>
            <college code="B"><students>50</students><courses>10</courses><shared>4</shared><cross>18</cross></college>
            <college code="C"><students>50</students><courses>10</courses><shared>4</shared><cross>12</cross></college>
          </colleges>
          <topCourses>
            <course id="BC001" name="数据库原理" enrollments="28" origin="B"/>
            <course id="AC001" name="数据库原理" enrollments="26" origin="A"/>
          </topCourses>
        </analyticsReport>""";
    var d = StatsData.fromXml(XmlIO.parse(xml));
    assertEquals(150, d.summary().totalStudents());
    assertEquals(3, d.colleges().size());
    assertEquals("B", d.colleges().get(1).code());
    assertEquals(2, d.topCourses().size());
  }

  @Test void toXml_produces_valid_structure() {
    var s = new StatsData.Summary(150, 30, 12, 45);
    var c = new StatsData.CollegeStat("A", 50, 10, 4, 15);
    var top = new StatsData.CourseEntry("AC001", "DB", 28, "A");
    var data = new StatsData(s, java.util.List.of(c), java.util.List.of(top));
    String xml = data.toXml();
    assertTrue(xml.contains("<totalStudents>150</totalStudents>"));
    assertTrue(xml.contains("<college code=\"A\">"));
    assertTrue(xml.contains("<course id=\"AC001\""));
  }
}
