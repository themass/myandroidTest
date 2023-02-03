package com.ks.myapp.bean.form;

import java.io.Serializable;

/**
 * Created by themass on 2017/2/14.
 */

public class CustomeAddForm implements Serializable {
    public Integer id;
    public String title;
    public String url;
    public String schema;
    public int openPath;
    public String uri;

    public CustomeAddForm() {

    }

    public CustomeAddForm(Integer id, String title, String url) {
        this.id = id;
        this.title = title;
        this.url = url;
    }
}
