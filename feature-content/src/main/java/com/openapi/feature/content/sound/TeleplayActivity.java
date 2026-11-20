package com.openapi.feature.content.sound;


import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.View;

import com.openapi.common.util.log.LogUtil;
import com.openapi.feature.content.R;
import com.openapi.core.legacyui.app.BaseFragmentActivity;
import com.openapi.feature.content.video.VideoChannleBodyFragment;

/**
 * Created by openapi on 2015/9/1.
 *
 * 「电视剧」频道页。早期实现是 SoundChannleActivity 的复制粘贴，忘了换 body fragment：
 * 里面装的是 ImgChannleBodyFragment（图片频道），用户点进来看到的是图片列表。
 * 现在装 VideoChannleBodyFragment——它和 VideoChannleActivity 一样按配置里的
 * channel 参数请求视频频道接口（teleplay_ 系列频道就是这么下发的）。
 */
public class TeleplayActivity extends BaseFragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        setFabUpVisibility(View.VISIBLE);
        Fragment fragment = null;
        try {
            fragment = VideoChannleBodyFragment.class.getDeclaredConstructor().newInstance();
            Bundle bundle = new Bundle();
            fragment.setArguments(getIntent().getExtras());
        } catch (Exception e) {
            LogUtil.e(e);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss();
        setToolbarTitle(R.string.tv, true);

    }

    public boolean needShow() {
        return true;
    }

    protected boolean enableSliding() {
        return true;
    }

}
