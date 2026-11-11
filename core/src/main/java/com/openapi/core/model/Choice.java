package com.openapi.core.model;

public class Choice {
    private String id;
    private Message message;

    public Choice() {
    }



    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
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
                ", message=" + message +
                '}';
    }
}
