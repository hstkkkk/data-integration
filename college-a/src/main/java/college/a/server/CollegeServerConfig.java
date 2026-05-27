package college.a.server;

// 注：college 模块不依赖 integration 模块。CollegeServerConfig 只持有
// integration host/port 字符串，跨院转发由后续任务用普通 Socket 完成。

public class CollegeServerConfig {
  public final String collegeCode;      // "A", "B", or "C"
  public final String integrationHost;
  public final int integrationPort;
  public final String courseIdPrefix;   // "AC", "BC", or "CC"
  public final String studentIdPrefix;  // "AS", "BS", or "CS"

  public CollegeServerConfig(String collegeCode, String courseIdPrefix, String studentIdPrefix) {
    this.collegeCode = collegeCode;
    this.courseIdPrefix = courseIdPrefix;
    this.studentIdPrefix = studentIdPrefix;
    this.integrationHost = System.getProperty("integration.host", "127.0.0.1");
    this.integrationPort = Integer.parseInt(System.getProperty("integration.port", "9100"));
  }

  /** 判断课程编号是否属于本院 */
  public boolean isLocalCourse(String courseId) {
    return courseId != null && courseId.startsWith(courseIdPrefix);
  }
}
