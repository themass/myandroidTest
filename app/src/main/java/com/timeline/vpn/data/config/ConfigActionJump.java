package com.timeline.vpn.data.config;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.base.WebViewActivity;
import com.timeline.vpn.ui.config.QuickBrowserConfigActivity;
import com.timeline.vpn.ui.sound.ImgChannleActivity;
import com.timeline.vpn.ui.sound.SoundChannleActivity;
import com.timeline.vpn.ui.sound.TextChannleActivity;
import com.timeline.vpn.ui.sound.VideoActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.timeline.vpn.constant.Constants.HTTPS_URL;
import static com.timeline.vpn.constant.Constants.HTTP_URL;

/**
 * Created by themass on 2016/3/17.
 */
public class ConfigActionJump {

    public static Map<String, Class<? extends AppCompatActivity>> configMap = new HashMap<>();

    static {
        configMap.put(Constants.HTTP_URL, WebViewActivity.class);
        configMap.put(Constants.HTTPS_URL, WebViewActivity.class);
        configMap.put(Constants.BROWSER_URL, QuickBrowserConfigActivity.class);
        configMap.put(Constants.SOUND_URL, SoundChannleActivity.class);
        configMap.put(Constants.TEXT_URL, TextChannleActivity.class);
        configMap.put(Constants.IMG_URL, ImgChannleActivity.class);
        configMap.put(Constants.VIDEO_URL, VideoActivity.class);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ConfigActionEvent event) {
        LogUtil.i("ConfigActionJump onEvent-" + event.url + "-" + Thread.currentThread().getName());
        Uri uri = Uri.parse(event.url);
        Set<String> names = uri.getQueryParameterNames();
        HashMap<String, String> param = new HashMap<>();
        for (String name : names) {
            param.put(name, uri.getQueryParameter(name));
        }
        Class<? extends AppCompatActivity> activity = configMap.get(uri.getScheme());
        if (activity != null) {
            Boolean adsShow = event.param != null ? (Boolean) event.param.get(Constants.ADS_SHOW_CONFIG) : null;
            Boolean adsPopShow = event.param != null ? (Boolean) event.param.get(Constants.ADS_POP_SHOW_CONFIG) : null;
            Intent intent = new Intent(event.context, activity);
            intent.putExtra(Constants.URL, parserUrl(uri.getScheme(), event.url, param));
            intent.putExtra(Constants.ADS_SHOW_CONFIG, adsShow);
            intent.putExtra(Constants.ADS_POP_SHOW_CONFIG, adsPopShow);
            intent.putExtra(Constants.CONFIG_PARAM, param);
            intent.putExtra(Constants.TITLE, event.title);
            event.context.startActivity(intent);
        } else {
            Toast.makeText(event.context, R.string.version_low, Toast.LENGTH_SHORT).show();
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
