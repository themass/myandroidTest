package com.openapi.ks.myapp.data.config;

import java.util.function.Function;

import chat.ui.data.model.Message;

/**
 * Created by openapi on 2016/3/17.
 */
public class TtsLoadEvent {
    public Message message;
    public Function<Message,Void> function;
}
