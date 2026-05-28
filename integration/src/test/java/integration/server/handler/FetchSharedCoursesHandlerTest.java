package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;

class FetchSharedCoursesHandlerTest {

  private static final String A_RAW =
      "<课程集><课程><课程编号>AC001</课程编号><课程名称>Math</课程名称><课时>48</课时><学分>3</学分><授课老师>Zhao</授课老师><授课地点>A101</授课地点><共享>Y</共享></课程></课程集>";
  private static final String B_RAW =
      "<课程集><课程><编号>BC001</编号><名称>DB</名称><课时>32</课时><学分>3</学分><老师>Li</老师><地点>B101</地点><共享>Y</共享></课程></课程集>";
  private static final String C_RAW =
      "<courses><course><Cno>CC01</Cno><Cnm>Net</Cnm><Ctm>32</Ctm><Cpt>2</Cpt><Tec>Wang</Tec><Pla>C301</Pla><Share>Y</Share></course></courses>";

  private CollegeClient mockClient(String reply) throws IOException {
    var client = mock(CollegeClient.class);
    when(client.send(any())).thenReturn(Message.ok("r", reply));
    return client;
  }

  @Test
  void from_A_excludes_A_includes_B_and_C() throws IOException {
    var handler = new FetchSharedCoursesHandler(mockClient(A_RAW), mockClient(B_RAW), mockClient(C_RAW));
    var res = handler.handle(new Message(Command.FETCH_SHARED_COURSES, "r0", "<from>A</from>"));
    assertEquals(Command.OK, res.command());
    assertFalse(res.payload().contains("<id>AC001</id>"));
    assertTrue(res.payload().contains("<id>BC001</id>"));
    assertTrue(res.payload().contains("<id>CC01</id>"));
  }

  @Test
  void from_B_excludes_B_includes_A_and_C() throws IOException {
    var handler = new FetchSharedCoursesHandler(mockClient(A_RAW), mockClient(B_RAW), mockClient(C_RAW));
    var res = handler.handle(new Message(Command.FETCH_SHARED_COURSES, "r0", "<from>B</from>"));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<id>AC001</id>"));
    assertFalse(res.payload().contains("<id>BC001</id>"));
    assertTrue(res.payload().contains("<id>CC01</id>"));
  }

  @Test
  void from_C_excludes_C_includes_A_and_B() throws IOException {
    var handler = new FetchSharedCoursesHandler(mockClient(A_RAW), mockClient(B_RAW), mockClient(C_RAW));
    var res = handler.handle(new Message(Command.FETCH_SHARED_COURSES, "r0", "<from>C</from>"));
    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<id>AC001</id>"));
    assertTrue(res.payload().contains("<id>BC001</id>"));
    assertFalse(res.payload().contains("<id>CC01</id>"));
  }
}
