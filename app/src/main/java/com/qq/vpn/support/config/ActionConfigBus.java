package com.qq.vpn.support.config;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;

import com.qq.Constants;
import com.qq.ext.util.LogUtil;
import com.qq.vpn.main.ui.WebViewActivity;
import com.qq.vpn.ui.base.actvity.QuickBrowserConfigActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.qq.Constants.HTTPS_URL;
import static com.qq.Constants.HTTP_URL;


/**
 * Created by dengt on 2016/3/17.
 */
public class ActionConfigBus {

    public static Map<String, Class<? extends AppCompatActivity>> configMap = new HashMap<>();

    static {
        configMap.put(Constants.HTTP_URL, WebViewActivity.class);
        configMap.put(Constants.HTTPS_URL, WebViewActivity.class);
        configMap.put(Constants.BROWSER_URL, QuickBrowserConfigActivity.class);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConfigActionEvent event) {
        LogUtil.i("ActionConfigBus onEvent-" + event.url + "-" + Thread.currentThread().getName());
        Uri uri = Uri.parse(event.url);
        Set<String> names = uri.getQueryParameterNames();
        HashMap<String, String> param = new HashMap<>();
        for (String name : names) {
            param.put(name, uri.getQueryParameter(name));
        }
        Class<? extends AppCompatActivity> activity = configMap.get(uri.getScheme());
        if (activity != null) {
            Intent intent = new Intent(event.context, activity);
            intent.putExtra(Constants.URL, parserUrl(uri.getScheme(), event.url, param));
            intent.putExtra(Constants.CONFIG_PARAM, param);
            intent.putExtra(Constants.TITLE, event.title);
            event.context.startActivity(intent);
        }
    }

    private String parserUrl(String schema, String url, Map<String, String> param) {
        if (HTTP_URL.equals(schema) || HTTPS_URL.equals(schema)) {
            return url;
        } else {
            try {
                return URLDecoder.decode(param.get(Constants.URL), "utf-8");
            } catch (Exception e) {
                LogUtil.e(e);
                return url;
            }
        }
    }
}
