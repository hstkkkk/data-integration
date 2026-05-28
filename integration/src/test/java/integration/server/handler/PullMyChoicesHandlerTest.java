package integration.server.handler;

import cn.edu.di.protocol.Command;
import cn.edu.di.protocol.Message;
import integration.net.CollegeClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PullMyChoicesHandlerTest {

  private static final String B_REPLY =
      "<myChoiceSet sno=\"AS001\">"
      + "<课程><编号>BC003</编号><名称>OS</名称>"
      + "<课时>32</课时><学分>3</学分>"
      + "<老师>Wang</老师><地点>B201</地点>"
      + "<学号>AS001</学号><得分></得分></课程>"
      + "</myChoiceSet>";

  private static final String C_REPLY =
      "<myChoiceSet sno=\"AS001\">"
      + "<course><Cno>CC005</Cno><Cnm>Net</Cnm>"
      + "<Ctm>32</Ctm><Cpt>2</Cpt>"
      + "<Tec>Li</Tec><Pla>C301</Pla>"
      + "<Sno>AS001</Sno><Grd></Grd></course>"
      + "</myChoiceSet>";

  private CollegeClient mockOk(String reply) throws IOException {
    var c = mock(CollegeClient.class);
    when(c.send(any())).thenReturn(Message.ok("r", reply));
    return c;
  }

  @Test
  void home_A_only_calls_B_and_C() throws IOException {
    var clientA = mock(CollegeClient.class);
    var clientB = mockOk(B_REPLY);
    var clientC = mockOk(C_REPLY);
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"AS001\" home=\"A\"/>"));

    assertEquals(Command.OK, res.command());
    verify(clientA, never()).send(any());
    verify(clientB, times(1)).send(any());
    verify(clientC, times(1)).send(any());
    assertTrue(res.payload().contains("<class origin=\"B\">"));
    assertTrue(res.payload().contains("<class origin=\"C\">"));
    assertTrue(res.payload().contains("<id>BC003</id>"));
    assertTrue(res.payload().contains("<id>CC005</id>"));
  }

  @Test
  void home_B_only_calls_A_and_C() throws IOException {
    var clientA = mockOk("<myChoiceSet sno=\"BS001\"></myChoiceSet>");
    var clientB = mock(CollegeClient.class);
    var clientC = mockOk(C_REPLY);
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"BS001\" home=\"B\"/>"));

    assertEquals(Command.OK, res.command());
    verify(clientB, never()).send(any());
    verify(clientA, times(1)).send(any());
    verify(clientC, times(1)).send(any());
  }

  @Test
  void home_C_only_calls_A_and_B() throws IOException {
    var clientA = mockOk("<myChoiceSet sno=\"CS001\"></myChoiceSet>");
    var clientB = mockOk(B_REPLY);
    var clientC = mock(CollegeClient.class);
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"CS001\" home=\"C\"/>"));

    assertEquals(Command.OK, res.command());
    verify(clientC, never()).send(any());
  }

  @Test
  void college_returns_err_yields_error_entry() throws IOException {
    var clientA = mock(CollegeClient.class);
    var clientB = mockOk(B_REPLY);
    var clientC = mock(CollegeClient.class);
    when(clientC.send(any())).thenReturn(Message.err("r", "DB_DOWN", "oops"));
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"AS001\" home=\"A\"/>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<class origin=\"B\">"));
    assertTrue(res.payload().contains("<error college=\"C\">"));
  }

  @Test
  void college_throws_yields_error_entry() throws IOException {
    var clientA = mock(CollegeClient.class);
    var clientB = mockOk(B_REPLY);
    var clientC = mock(CollegeClient.class);
    when(clientC.send(any())).thenThrow(new IOException("Connection refused"));
    var handler = new PullMyChoicesHandler(clientA, clientB, clientC);

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "<myChoicesReq sno=\"AS001\" home=\"A\"/>"));

    assertEquals(Command.OK, res.command());
    assertTrue(res.payload().contains("<error college=\"C\">"));
  }

  @Test
  void bad_payload_returns_err() {
    var handler = new PullMyChoicesHandler(
        mock(CollegeClient.class), mock(CollegeClient.class), mock(CollegeClient.class));

    var res = handler.handle(new Message(Command.PULL_MY_CHOICES, "r0",
        "not valid xml"));

    assertEquals(Command.ERR, res.command());
    assertTrue(res.payload().contains("BAD_PAYLOAD"));
  }
}
