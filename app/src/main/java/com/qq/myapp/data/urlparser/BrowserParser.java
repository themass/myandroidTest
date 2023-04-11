package com.qq.myapp.data.urlparser;

import android.net.Uri;

import com.qq.common.util.LogUtil;
import com.qq.myapp.bean.form.CustomeAddForm;
import com.qq.myapp.constant.Constants;

import java.net.URLDecoder;

/**
 * Created by dengt on 2017/4/10.
 */

public class BrowserParser implements UrlParser {
    @Override
    public CustomeAddForm parser(String url) {
        CustomeAddForm form = new CustomeAddForm();
        try {
            Uri uri = Uri.parse(url);
            String path = URLDecoder.decode(uri.getQueryParameter("url"), "utf-8");
            uri = Uri.parse(path);
            form.schema = uri.getScheme();
            form.uri = path.replace(form.schema + Constants.URL_TMP, "");
            form.openPath = Constants.OpenUrlPath.browser;
        } catch (Exception e) {
            LogUtil.e(e);
        }
        return form;
    }

    @Override
    public boolean canParser(String url) {
        return url.startsWith(Constants.BROWSER_URL);
    }
}