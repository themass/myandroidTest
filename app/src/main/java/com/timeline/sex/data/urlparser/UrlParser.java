package com.timeline.sex.data.urlparser;

import com.timeline.sex.bean.form.CustomeAddForm;

/**
 * Created by themass on 2017/4/10.
 */

public interface UrlParser {
    CustomeAddForm parser(String url);

    boolean canParser(String url);
}
