package com.openapi.ks.myapp.bean.vo;


/**
 * @Author： liguoqing
 * @Date： 2025/2/9 19:10
 * @Describe：
 */
public class TtsResponseVo {
    private String id;
    private String data;
    private String fileName;
    private String lang;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }
}
