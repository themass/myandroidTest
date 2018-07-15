package com.timeline.view.data.urlparser;

import com.timeline.view.bean.form.CustomeAddForm;

/**
 * Created by themass on 2017/4/10.
 */

public interface UrlParser {
    CustomeAddForm parser(String url);

    boolean canParser(String url);
}
