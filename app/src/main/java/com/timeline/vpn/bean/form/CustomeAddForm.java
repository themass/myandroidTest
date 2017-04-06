package com.timeline.vpn.bean.form;

import java.io.Serializable;

/**
 * Created by themass on 2017/2/14.
 */

public class CustomeAddForm implements Serializable{
    public Integer id;
    public String title;
    public String url;

    public CustomeAddForm(Integer id, String title, String url) {
        this.title = title;
        this.url = url;
    }
}
