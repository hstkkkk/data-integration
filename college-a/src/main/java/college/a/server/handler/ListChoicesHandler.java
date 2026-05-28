package college.a.server.handler;

import cn.edu.di.protocol.Message;
import college.a.dao.ChoiceDao;
import college.a.xml.ChoiceAAdapter;

public class ListChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;

  public ListChoicesHandler(ChoiceDao choiceDao) {
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    return Message.ok(req.requestId(), ChoiceAAdapter.marshal(choiceDao.findAll()));
  }
}
