package com.openapi.feature.content.config;

import android.content.Intent;
import android.net.Uri;

import com.openapi.core.config.CommonConstants;
import com.openapi.core.event.ConfigActionEvent;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.core.R;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.base.WebViewActivity;
import com.openapi.feature.content.config.QuickBrowserConfigActivity;
import com.openapi.feature.content.image.ImgChannleActivity;
import com.openapi.feature.content.sound.SoundChannleActivity;
import com.openapi.feature.content.sound.TeleplayActivity;
import com.openapi.feature.content.text.TextChannleActivity;
import com.openapi.feature.content.video.VideoChannleActivity;

import com.openapi.core.event.CoreEvents;
import com.openapi.common.event.EventFlowsKt;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.openapi.core.config.Constants.HTTPS_URL;
import static com.openapi.core.config.Constants.HTTP_URL;

/**
 * Created by openapi on 2016/3/17.
 */
public class ConfigActionJump {

    /** 页面的类名：本模块和 :core 的页面用 class；:feature-video 和 :app 的页面只用类名，避免依赖。 */
    private static final String VIDEO_LIST_SHOW = "com.openapi.feature.video.page.VideoListShowActivity";
    private static final String HOME = "com.openapi.app.home.HomeActivity";

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
        // 服务端电影卡仍下发 videoList1://…；以前只注册了 videoList，未知 scheme 被误报成「版本太低」
        configMap.put(Constants.VIDEOLIST_URL1, VIDEO_LIST_SHOW);
        configMap.put(Constants.TELEPLAY_URL, TeleplayActivity.class.getName());
        // 服务端可下发 movie:// 走电影频道页；Constants.LOCAL_MOVIE_URL 早就定义了，
        // 但路由一直没注册，点进来只会误报「版本太低」。
        configMap.put(Constants.LOCAL_MOVIE_URL, "com.openapi.feature.content.video.MovieChannleActivity");
        configMap.put(Constants.LOCAL_CHAT, HOME);
        configMap.put(Constants.LOCAL_CHAT2, HOME);
        // 用户上传频道：进视频频道页，内部再按 actionUrl/param 分流
        configMap.put(Constants.VIDEO_CHANNEL_USER_URL, VideoChannleActivity.class.getName());
        configMap.put(Constants.LOCAL_SOUND, "com.openapi.retromusic.activities.MainActivity");
        configMap.put(Constants.LOCAL_SOUND2, "com.openapi.retromusic.activities.MainActivity");
    }

    /** 在进程级作用域里收集配置页跳转事件；应用启动时调用一次。 */
    public static void start() {
        EventFlowsKt.observeJava(EventFlowsKt.getAppScope(), CoreEvents.INSTANCE.getConfigAction(),
                event -> new ConfigActionJump().onEvent(event));
    }

    private void onEvent(ConfigActionEvent event) {
        LogUtil.i("ConfigActionJump onEvent-" + event.url + "-" + Thread.currentThread().getName());
        String rawUrl = event.url;
        // 相对路径（/cat/1023.html）没有 scheme，以前会误报「版本太低」；尽量用事件里的 baseurl 拼绝对地址
        if (rawUrl != null && !rawUrl.contains(":") && event.param != null) {
            Object base = event.param.get("baseurl");
            if (base instanceof String) {
                String joined = com.openapi.core.model.RecommendUrl.resolve((String) base, rawUrl);
                if (joined != null) {
                    rawUrl = joined;
                }
            }
        }
        Uri uri = Uri.parse(rawUrl);
        Set<String> names = uri.getQueryParameterNames();
        HashMap<String, String> param = new HashMap<>();
        for (String name : names) {
            param.put(name, uri.getQueryParameter(name));
        }
        String scheme = uri.getScheme();
        String activityName = configMap.get(scheme);
        if (activityName != null) {
            Boolean adsShow = event.param != null ? (Boolean) event.param.get(CommonConstants.ADS_SHOW_CONFIG) : null;
            Boolean adsPopShow = event.param != null ? (Boolean) event.param.get(CommonConstants.ADS_POP_SHOW_CONFIG) : null;
            Intent intent = new Intent().setClassName(event.context, activityName);
            intent.putExtra(Constants.URL, parserUrl(scheme, rawUrl, param));
            intent.putExtra(CommonConstants.ADS_SHOW_CONFIG, adsShow);
            intent.putExtra(CommonConstants.ADS_POP_SHOW_CONFIG, adsPopShow);
            intent.putExtra(Constants.CONFIG_PARAM, param);
            intent.putExtra(Constants.TITLE, event.title);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            event.context.startActivity(intent);
        } else {
            // 真正的版本门槛在卡片 minVersion 检查；这里只是客户端不认识的跳转协议
            LogUtil.e("ConfigActionJump unknown scheme=" + scheme + " url=" + event.url);
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
