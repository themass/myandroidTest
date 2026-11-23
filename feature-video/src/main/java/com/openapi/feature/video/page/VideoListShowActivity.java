package com.openapi.feature.video.page;


import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;

import com.openapi.common.util.log.LogUtil;
import com.openapi.feature.video.R;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.app.BaseFragmentActivity;
import com.openapi.core.legacyui.features.BasePullLoadbleFragment;
import com.openapi.feature.video.list.GSYAutoVideoListFragment;

import java.util.HashMap;

/**
 * Created by openapi on 2015/9/1.
 *
 * 基类从 AppCompatActivity 换成 BaseFragmentActivity：旧实现没有工具栏，
 * 用户进到这个由服务端配置打开的页面后没有任何返回入口（只能靠系统返回键）。
 */
public class VideoListShowActivity extends BaseFragmentActivity {
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
            // 小窗开关两种取值都用同一个 GSY 列表（原 AutoVideoListFragment 是它的空子类，已删除）。
            BasePullLoadbleFragment fragment = GSYAutoVideoListFragment.class.newInstance();
            fragment.putSerializable(vo.get(Constants.CHANNEL));
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment, fragment)
                    .commitAllowingStateLoss();

        } catch (Exception e) {
            Log.e("", "", e);
        }
        String title = getIntent().getStringExtra(Constants.TITLE);
        if (title != null && !title.isEmpty()) {
            setToolbarTitle(title, true);
        } else {
            setToolbarTitle(R.string.video, true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }
}
