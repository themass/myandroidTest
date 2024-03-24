package com.openapi.ks.myapp.bean.vo;

public class Choice {
    private String id;
    private int index;
    private Message message;
    private String logprobs;
    private String finish_reason;

    public Choice() {
    }


    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String getLogprobs() {
        return logprobs;
    }

    public void setLogprobs(String logprobs) {
        this.logprobs = logprobs;
    }

    public String getFinish_reason() {
        return finish_reason;
    }

    public void setFinish_reason(String finish_reason) {
        this.finish_reason = finish_reason;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "Choice{" +
                "id='" + id + '\'' +
                ", index=" + index +
                ", message=" + message +
                ", logprobs='" + logprobs + '\'' +
                ", finish_reason='" + finish_reason + '\'' +
                '}';
    }
}
