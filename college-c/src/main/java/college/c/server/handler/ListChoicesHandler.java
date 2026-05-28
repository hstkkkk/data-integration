package college.c.server.handler;

import cn.edu.di.protocol.Message;
import college.c.dao.ChoiceDao;
import college.c.xml.ChoiceCAdapter;

public class ListChoicesHandler implements Handler {
  private final ChoiceDao choiceDao;

  public ListChoicesHandler(ChoiceDao choiceDao) {
    this.choiceDao = choiceDao;
  }

  @Override
  public Message handle(Message req) {
    return Message.ok(req.requestId(), ChoiceCAdapter.marshal(choiceDao.findAll()));
  }
}
