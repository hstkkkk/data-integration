package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;

class FetchSharedCoursesHandlerTest {
  @Test
  void fetches_from_other_colleges() throws IOException {
    var clientB = mock(CollegeClient.class);
    var clientC = mock(CollegeClient.class);
    when(clientB.send(any())).thenReturn(Message.ok("r1",
        "<课程集><课程><编号>BC001</编号><名称>DB</名称><课时>32</课时><学分>3</学分><老师>Li</老师><地点>B101</地点><共享>Y</共享></课程></课程集>"));
    when(clientC.send(any())).thenReturn(Message.ok("r2",
        "<courses><course><Cno>CC01</Cno><Cnm>Net</Cnm><Ctm>32</Ctm><Cpt>2</Cpt><Tec>Wang</Tec><Pla>C301</Pla><Share>Y</Share></course></courses>"));

    var handler = new FetchSharedCoursesHandler(clientB, clientC);
    var res = handler.handle(new Message(Command.FETCH_SHARED_COURSES, "r0", "<from>A</from>"));
    assertEquals(Command.OK, res.command());
  }
}
