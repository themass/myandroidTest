package com.openapi.ks.myapp.data.urlparser;

import com.openapi.ks.myapp.bean.form.CustomeAddForm;

/**
 * Created by openapi on 2017/4/10.
 */

public interface UrlParser {
    CustomeAddForm parser(String url);

    boolean canParser(String url);
}
