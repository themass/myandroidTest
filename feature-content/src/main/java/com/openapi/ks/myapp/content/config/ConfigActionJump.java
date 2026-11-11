package com.openapi.ks.myapp.content.config;

import android.content.Intent;
import android.net.Uri;

import com.openapi.core.CommonConstants;
import com.openapi.core.event.ConfigActionEvent;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.ToastUtil;
import com.openapi.core.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.base.WebViewActivity;
import com.openapi.ks.myapp.content.config.QuickBrowserConfigActivity;
import com.openapi.ks.myapp.content.image.ImgChannleActivity;
import com.openapi.ks.myapp.content.sound.SoundChannleActivity;
import com.openapi.ks.myapp.content.sound.TeleplayActivity;
import com.openapi.ks.myapp.content.text.TextChannleActivity;
import com.openapi.ks.myapp.content.video.VideoChannleActivity;

import com.openapi.core.event.CoreEvents;
import com.openapi.common.event.EventFlowsKt;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.openapi.ks.myapp.constant.Constants.HTTPS_URL;
import static com.openapi.ks.myapp.constant.Constants.HTTP_URL;

/**
 * Created by openapi on 2016/3/17.
 */
public class ConfigActionJump {

    /** 页面的类名：本模块和 :core 的页面用 class；:feature-video 和 :app 的页面只用类名，避免依赖。 */
    private static final String VIDEO_LIST_SHOW = "com.openapi.ks.myapp.video.page.VideoListShowActivity";
    private static final String HOME = "com.openapi.home.HomeActivity";

    private static final Map<String, String> configMap = new HashMap<>();

    static {
        configMap.put(Constants.HTTP_URL, WebViewActivity.class.getName());
        configMap.put(Constants.HTTPS_URL, WebViewActivity.class.getName());
        configMap.put(Constants.BROWSER_URL, QuickBrowserConfigActivity.class.getName());
        configMap.put(Constants.SOUND_URL, SoundChannleActivity.class.getName());
        configMap.put(Constants.TEXT_URL, TextChannleActivity.class.getName());
        configMap.put(Constants.IMG_URL, ImgChannleActivity.class.getName());
        configMap.put(Constants.VIDEO_URL, VideoChannleActivity.class.getName());
        configMap.put(Constants.VIDEOLIST_URL, VIDEO_LIST_SHOW);
        configMap.put(Constants.TELEPLAY_URL, TeleplayActivity.class.getName());
        configMap.put(Constants.LOCAL_CHAT, HOME);
        configMap.put(Constants.LOCAL_CHAT2, HOME);

//        configMap.put(Constants.VIDEO_CHANNEL_USER_URL, VideoChannleUserActivity.class);
    }

    /** 在进程级作用域里收集配置页跳转事件；应用启动时调用一次。 */
    public static void start() {
        EventFlowsKt.observeJava(EventFlowsKt.getAppScope(), CoreEvents.INSTANCE.getConfigAction(),
                event -> new ConfigActionJump().onEvent(event));
    }

    private void onEvent(ConfigActionEvent event) {
        LogUtil.i("ConfigActionJump onEvent-" + event.url + "-" + Thread.currentThread().getName());
        Uri uri = Uri.parse(event.url);
        Set<String> names = uri.getQueryParameterNames();
        HashMap<String, String> param = new HashMap<>();
        for (String name : names) {
            param.put(name, uri.getQueryParameter(name));
        }
        String activityName = configMap.get(uri.getScheme());
        if (activityName != null) {
            Boolean adsShow = event.param != null ? (Boolean) event.param.get(CommonConstants.ADS_SHOW_CONFIG) : null;
            Boolean adsPopShow = event.param != null ? (Boolean) event.param.get(CommonConstants.ADS_POP_SHOW_CONFIG) : null;
            Intent intent = new Intent().setClassName(event.context, activityName);
            intent.putExtra(Constants.URL, parserUrl(uri.getScheme(), event.url, param));
            intent.putExtra(CommonConstants.ADS_SHOW_CONFIG, adsShow);
            intent.putExtra(CommonConstants.ADS_POP_SHOW_CONFIG, adsPopShow);
            intent.putExtra(Constants.CONFIG_PARAM, param);
            intent.putExtra(Constants.TITLE, event.title);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            event.context.startActivity(intent);
        } else {
            ToastUtil.showShort(R.string.version_low);
        }
    }

    private String parserUrl(String schema, String url, Map<String, String> param) {
        if (HTTP_URL.equals(schema) || HTTPS_URL.equals(schema)) {
            return url;
        }
        String encoded = param != null ? param.get(Constants.URL) : null;
        if (encoded == null || encoded.isEmpty()) {
            // 例如 sound://config?channel=nvyou-main 无 url 参数，decode 不可对 null 调用
            return url;
        }
        try {
            return URLDecoder.decode(encoded, "UTF-8");
        } catch (Exception e) {
            LogUtil.e(e);
            return url;
        }
    }
}
