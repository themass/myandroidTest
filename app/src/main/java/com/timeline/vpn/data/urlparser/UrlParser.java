package com.timeline.vpn.data.urlparser;

import com.timeline.vpn.bean.form.CustomeAddForm;

/**
 * Created by themass on 2017/4/10.
 */

public interface UrlParser {
    public CustomeAddForm parser(String url);
    public boolean canParser(String url);
}
