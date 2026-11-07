package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.widget.FrameLayout;

import com.openapi.core.util.LogUtil;
import com.openapi.core.util.PreferenceUtils;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.myapp.ui.fragment.AutoVideoListFragment;
import com.openapi.ks.myapp.ui.gsy.GSYAutoVideoListFragment;
//import com.openapi.ks.myapp.ui.gsy.GSYAutoVideoListFragment;

import java.util.HashMap;

/**
 * Created by openapi on 2015/9/1.
 */
public class VideoListShowActivity extends AppCompatActivity {
    private HashMap<String, String> vo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        // 兼容性处理：API 33+ 使用新方法，否则使用旧方法
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vo = getIntent().getSerializableExtra(Constants.CONFIG_PARAM, HashMap.class);
        } else {
            vo = (HashMap<String, String>) getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        }
        try {
            BasePullLoadbleFragment fragment;
            boolean needTiny = PreferenceUtils.getPrefBoolean(this, Constants.LISTVIDEO_TINY_SWITCH, true);
            if (!needTiny) {
                fragment = AutoVideoListFragment.class.newInstance();
            } else {

                fragment = GSYAutoVideoListFragment.class.newInstance();
            }
            fragment.putSerializable(vo.get(Constants.CHANNEL));
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment, fragment)
                    .commitAllowingStateLoss();

        } catch (Exception e) {
            Log.e("", "", e);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }
}
