package college.b.server.handler;

import cn.edu.di.protocol.Message;
import college.b.dao.ChoiceDao;
import college.b.xml.ChoiceBAdapter;

public class ListChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;

  public ListChoicesHandler(ChoiceDao choiceDao) {
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    return Message.ok(req.requestId(), ChoiceBAdapter.marshal(choiceDao.findAll()));
  }
}
