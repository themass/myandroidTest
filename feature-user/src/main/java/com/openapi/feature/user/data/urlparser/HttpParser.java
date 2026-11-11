package com.openapi.feature.user.data.urlparser;

import android.net.Uri;

import com.openapi.core.model.CustomeAddForm;
import com.openapi.core.config.Constants;

/**
 * Created by openapi on 2017/4/10.
 */

public class HttpParser implements UrlParser {
    @Override
    public CustomeAddForm parser(String url) {
        CustomeAddForm form = new CustomeAddForm();
        Uri uri = Uri.parse(url);
        form.schema = uri.getScheme();
        form.uri = url.replace(form.schema + Constants.URL_TMP, "");
        form.openPath = Constants.OpenUrlPath.local;
        return form;
    }

    @Override
    public boolean canParser(String url) {
        return url.startsWith(Constants.HTTP_URL);
    }
}
