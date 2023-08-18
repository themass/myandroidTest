package com.openapi.ks.myapp.data.config;

import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.ToastUtil;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.base.WebViewActivity;
import com.openapi.ks.myapp.ui.config.QuickBrowserConfigActivity;
import com.openapi.ks.myapp.ui.sound.ImgChannleActivity;
import com.openapi.ks.myapp.ui.sound.SoundChannleActivity;
import com.openapi.ks.myapp.ui.sound.TeleplayActivity;
import com.openapi.ks.myapp.ui.sound.TextChannleActivity;
import com.openapi.ks.myapp.ui.sound.VideoChannleActivity;
import com.openapi.ks.myapp.ui.sound.VideoListShowActivity;
import com.rks.musicx.ui.activities.MusicXMainActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.ryanhoo.music.ui.main.MuiscMainActivity;

import static com.openapi.ks.myapp.constant.Constants.HTTPS_URL;
import static com.openapi.ks.myapp.constant.Constants.HTTP_URL;

/**
 * Created by openapi on 2016/3/17.
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
        configMap.put(Constants.VIDEO_URL, VideoChannleActivity.class);
        configMap.put(Constants.VIDEOLIST_URL, VideoListShowActivity.class);
        configMap.put(Constants.TELEPLAY_URL, TeleplayActivity.class);

        configMap.put(Constants.LOCAL_SOUND , MuiscMainActivity.class);
        configMap.put(Constants.LOCAL_SOUND2 , MusicXMainActivity.class);
//        configMap.put(Constants.VIDEO_CHANNEL_USER_URL, VideoChannleUserActivity.class);
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
            ToastUtil.showShort( R.string.version_low);
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
