package seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SeedA {
  private SeedA() {}

  public record Student(String id, String name, String sex, String dept, String account) {}
  public record Course(String id, String name, int hours, double score, String teacher, String location, boolean shared) {}
  public record Choice(String studentId, String courseId) {}
  public record Data(List<Student> students, List<Course> courses, List<Choice> choices) {}

  private static final String[] SURNAMES = {"张","王","李","赵","陈","刘","杨","黄","周","吴"};
  private static final String[] GIVEN = {"伟","芳","娜","敏","静","秀英","丽","强","磊","军","洋","勇","艳","杰","涛","明","超","秀兰","霞","平"};

  public static Data generate(long seed) {
    Random r = new Random(seed);
    List<Student> students = new ArrayList<>();
    for (int i = 1; i <= 50; i++) {
      String id = String.format("AS%03d", i);
      String name = SURNAMES[r.nextInt(SURNAMES.length)] + GIVEN[r.nextInt(GIVEN.length)];
      String sex = r.nextBoolean() ? "男" : "女";
      students.add(new Student(id, name, sex, "计算机", id.toLowerCase()));
    }

    String[] cnames = {"数据库原理","编译原理","操作系统","算法分析","计算机网络",
                       "软件工程","人工智能","机器学习","离散数学","数据结构"};
    List<Course> courses = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      String id = String.format("AC%03d", i + 1);
      boolean shared = i < 4; // first 4 are shared
      courses.add(new Course(id, cnames[i], 32 + r.nextInt(17),
          2.0 + r.nextInt(3), "老师" + (i % 3 + 1), "A" + (101 + i), shared));
    }

    List<Choice> choices = new ArrayList<>();
    for (Student s : students) {
      List<Course> shuffled = new ArrayList<>(courses);
      Collections.shuffle(shuffled, r);
      for (int k = 0; k < 5; k++) {
        choices.add(new Choice(s.id(), shuffled.get(k).id()));
      }
    }
    return new Data(students, courses, choices);
  }

  public static String toSql(Data d) {
    StringBuilder sb = new StringBuilder();
    // Accounts
    for (Student s : d.students()) {
      sb.append(String.format("INSERT INTO 账户(账户名,密码,权限) VALUES('%s','%s','stu');\n",
          s.account(), "pwd" + s.id().substring(2)));
    }
    sb.append("INSERT INTO 账户(账户名,密码,权限) VALUES('admin','admin1','adm');\n");
    // Students
    for (Student s : d.students()) {
      sb.append(String.format("INSERT INTO 学生(学号,姓名,性别,院系,关联账户) VALUES('%s','%s','%s','%s','%s');\n",
          s.id(), s.name(), s.sex(), s.dept(), s.account()));
    }
    // Courses
    for (Course c : d.courses()) {
      sb.append(String.format("INSERT INTO 课程(课程编号,课程名称,课时,学分,授课老师,授课地点,共享) VALUES('%s','%s',%d,%.1f,'%s','%s','%s');\n",
          c.id(), c.name(), c.hours(), c.score(), c.teacher(), c.location(), c.shared() ? "Y" : "N"));
    }
    // Choices (50 students x 5 = 250)
    for (Choice ch : d.choices()) {
      sb.append(String.format("INSERT INTO 选课(课程编号,学生编号,来源) VALUES('%s','%s','A');\n",
          ch.courseId(), ch.studentId()));
    }
    return sb.toString();
  }

  public static void main(String[] args) throws IOException {
    long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
    Path out = Path.of("college-a/src/main/resources/sql/init_a_data.sql");
    Files.createDirectories(out.getParent());
    Files.writeString(out, toSql(generate(seed)));
    System.out.println("wrote " + out);
  }
}
