package chat.ui.data.model;

public class MsgContent {
    private String content;
    private String org;
    private int time;
    private String msgId;
    private String holdId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getHoldId() {
        return holdId;
    }

    public void setHoldId(String holdId) {
        this.holdId = holdId;
    }

    @Override
    public String toString() {
        return "MsgContent{" +
                "content='" + content + '\'' +
                ", org='" + org + '\'' +
                ", time=" + time +
                ", msgId='" + msgId + '\'' +
                ", holdId='" + holdId + '\'' +
                '}';
    }
}
