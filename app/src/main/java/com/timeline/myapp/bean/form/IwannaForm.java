package com.timeline.myapp.bean.form;

/**
 * Created by themass on 2016/9/7.
 */
public class IwannaForm {
    public String content;
    public int loca;

    public IwannaForm(String content) {
        this.content = content;
    }
    public IwannaForm(String content,int loca) {
        this.content = content;
        this.loca = loca;
    }
}
