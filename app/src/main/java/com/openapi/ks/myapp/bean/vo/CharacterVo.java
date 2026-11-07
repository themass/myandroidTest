package com.openapi.ks.myapp.bean.vo;


/**
 * Created by openapi on 2017/9/3.
 */
public class CharacterVo {
    public Long id;
    public String title;
    public String content;
    public String url;
    public String extra;
    /** 是否为当前用户自己创建的角色（服务端按 ownerUserId 计算），决定能否编辑/删除。 */
    public boolean mine;

    public boolean isMine() {
        return mine;
    }

    public void setMine(boolean mine) {
        this.mine = mine;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
