package seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SeedC {
  private SeedC() {}

  public record Student(String id, String name, String sex, String dept, String password) {}
  public record Course(String id, String name, int hours, double score, String teacher, String location, boolean shared) {}
  public record Choice(String studentId, String courseId) {}
  public record Data(List<Student> students, List<Course> courses, List<Choice> choices) {}

  private static final String[] SURNAMES = {"赵","钱","孙","周","吴","郑","王","冯","陈","褚"};
  private static final String[] GIVEN = {"建国","芳","娜","敏","建军","秀英","丽","强","磊","军"};

  public static Data generate(long seed) {
    Random r = new Random(seed);
    List<Student> students = new ArrayList<>();
    for (int i = 1; i <= 50; i++) {
      String id = String.format("CS%03d", i);
      String name = SURNAMES[r.nextInt(SURNAMES.length)] + GIVEN[r.nextInt(GIVEN.length)];
      String sex = r.nextBoolean() ? "M" : "F";
      students.add(new Student(id, name, sex, "计算机", "pwd" + id.substring(2)));
    }

    String[] cnames = {"Database","Compilers","OS","Algorithms","Networks",
                       "SE","AI","ML","DiscreteMath","DataStructure"};
    List<Course> courses = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      String id = String.format("CC%02d", i + 1);
      boolean shared = i < 4;
      courses.add(new Course(id, cnames[i], 32 + r.nextInt(17),
          2.0 + r.nextInt(3), "T" + (i % 3 + 1), "C" + (101 + i), shared));
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
      sb.append(String.format("INSERT INTO 账户(acc,passwd) VALUES('%s','%s');\n",
          s.id().toLowerCase(), s.password()));
    }
    sb.append("INSERT INTO 账户(acc,passwd) VALUES('admin','admin1');\n");
    // Students
    for (Student s : d.students()) {
      sb.append(String.format("INSERT INTO 学生(Sno,Snm,Sex,Sde,Pwd) VALUES('%s','%s','%s','%s','%s');\n",
          s.id(), s.name(), s.sex(), s.dept(), s.password()));
    }
    // Courses
    for (Course c : d.courses()) {
      sb.append(String.format("INSERT INTO 课程(Cno,Cnm,Ctm,Cpt,Tec,Pla,Share) VALUES('%s','%s',%d,'%.0f','%s','%s','%s');\n",
          c.id(), c.name(), c.hours(), c.score(), c.teacher(), c.location(), c.shared() ? "Y" : "N"));
    }
    // Choices
    for (Choice ch : d.choices()) {
      sb.append(String.format("INSERT INTO 选课(Cno,Sno) VALUES('%s','%s');\n",
          ch.courseId(), ch.studentId()));
    }
    return sb.toString();
  }

  public static void main(String[] args) throws IOException {
    long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
    Path out = Path.of("college-c/src/main/resources/sql/init_c_data.sql");
    Files.createDirectories(out.getParent());
    Files.writeString(out, toSql(generate(seed)));
    System.out.println("wrote " + out);
  }
}
