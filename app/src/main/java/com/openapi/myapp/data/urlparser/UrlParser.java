package com.openapi.myapp.data.urlparser;


import com.openapi.myapp.bean.form.CustomeAddForm;

/**
 * Created by dengt on 2017/4/10.
 */

public interface UrlParser {
    CustomeAddForm parser(String url);

    boolean canParser(String url);
}
