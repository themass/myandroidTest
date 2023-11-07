package com.openapi.ks.myapp.ui.gsy.ad;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.gsy.GSYAutoVideoListFragment;
import com.shuyu.gsyvideoplayer.GSYVideoADManager;
import com.shuyu.gsyvideoplayer.GSYVideoManager;

import java.util.HashMap;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by openapi on 2015/9/1.
 */
public class GSYADVideoListShowActivity extends AppCompatActivity {
    private Unbinder unbinder;
    private HashMap<String, String> vo;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_fragment);
        unbinder = ButterKnife.bind(this);
        vo = (HashMap<String, String>)getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
        FrameLayout layout  = (FrameLayout)findViewById(R.id.fragment);
        try {
            GSYADAutoVideoListFragment fragment = GSYADAutoVideoListFragment.class.newInstance();
            fragment.putSerializable(vo.get(Constants.CHANNEL));
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment, fragment)
                    .commitAllowingStateLoss();

        } catch (Exception e) {
            Log.e("","",e);
        }
    }
    @Override
    protected void onNewIntent(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }
    @Override
    public void onBackPressed() {
        if (GSYVideoADManager.backFromWindowFull(this)) {
            return;
        }
        if (GSYVideoManager.backFromWindowFull(this)) {
            return;
        }
        super.onBackPressed();
    }
}