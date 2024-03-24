package chat.asrassistant.model;

public class Message {

  public String id;

  public String role;

  public String content;
  /**
   * stop结束,其他未结束,
   */
  public String type;
  /**
   * 消息类型 heartBeat 心跳   本地发送 message 消息 normal  接收到的消息  resend  有消息需要重新发送
   */
  public String messageType;
  /**
   * 消息顺序下标
   */
  public Integer index;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return "Message{" +
            "id='" + id + '\'' +
            ", role='" + role + '\'' +
            ", content='" + content + '\'' +
            ", type='" + type + '\'' +
            ", messageType='" + messageType + '\'' +
            ", index=" + index +
            '}';
  }
}
