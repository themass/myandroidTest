package com.qq.vpn.domain.req;

/**
 * Created by dengt on 2016/9/7.
 */
public class IwannaForm {
    public String content;
    public int loca;

    public IwannaForm(String content) {
        this.content = content;
    }
    public IwannaForm(String content, int loca) {
        this.content = content;
        this.loca = loca;
    }
}