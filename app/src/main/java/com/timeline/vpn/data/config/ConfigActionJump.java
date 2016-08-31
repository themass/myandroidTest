package com.timeline.vpn.data.config;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.WebViewActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by gqli on 2016/3/17.
 */
public class ConfigActionJump {
    public static final String HTTP_URL = "http://";
    public static final String HTTPS_URL = "https://";
    public static final String WEBVIEW_URL = "webviewActivity";

    public static Map<String, Class<? extends AppCompatActivity>> configMap = new HashMap<>();

    static {
        configMap.put(WEBVIEW_URL, WebViewActivity.class);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConfigActionEvent event) {
        LogUtil.i("onEvent-" + event.url + "-" + Thread.currentThread().getName());
        Class<? extends AppCompatActivity> activity = getActivity(event.url);
        if (activity != null) {
            Intent intent = new Intent(event.context, activity);
            intent.putExtra(Constants.URL, event.url);
            event.context.startActivity(intent);
        }
    }

    protected Class<? extends AppCompatActivity> getActivity(String url) {
        if (url.startsWith(HTTP_URL) || url.startsWith(HTTPS_URL)) {
            return configMap.get(WEBVIEW_URL);
        } else {
            return configMap.get(url);
        }
    }
}
