package chat.ui.data.model;

public class MsgContent {
    private String content;
    private String org;
    private int time;
    private String msgId;
    private String holdId;
    private String fileName;
    private String fileSize;

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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        return "MsgContent{" +
                "content='" + content + '\'' +
                ", org='" + org + '\'' +
                ", time=" + time +
                ", msgId='" + msgId + '\'' +
                ", holdId='" + holdId + '\'' +
                ", fileName='" + fileName + '\'' +
                '}';
    }
}
