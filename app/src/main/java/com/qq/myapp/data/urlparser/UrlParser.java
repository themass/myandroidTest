package com.qq.myapp.data.urlparser;

import com.qq.myapp.bean.form.CustomeAddForm;

/**
 * Created by themass on 2017/4/10.
 */

public interface UrlParser {
    CustomeAddForm parser(String url);

    boolean canParser(String url);
}
