package com.openapi.chat.data.remote;


/**
 * @Author： liguoqing
 * @Date： 2025/2/9 19:10
 * @Describe：
 */

public class AsrResponseVo {
    private String id;
    private String text;
    private String prod;
    private String lang;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getProd() {
        return prod;
    }

    public void setProd(String prod) {
        this.prod = prod;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    @Override
    public String toString() {
        return "AsrResponseVo{" +
                "id='" + id + '\'' +
                ", text='" + text + '\'' +
                ", prod='" + prod + '\'' +
                ", lang='" + lang + '\'' +
                '}';
    }
}
