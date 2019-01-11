package com.qq.myapp.ui.sound;


import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.FrameLayout;

import com.qq.e.ads.nativ.NativeExpressAD;
import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.e.ads.nativ.NativeExpressMediaListener;
import com.qq.e.comm.constants.AdPatternType;
import com.qq.e.comm.pi.AdData;
import com.qq.e.comm.util.AdError;
import com.qq.e.comm.util.GDTLogger;
import com.qq.kuaibo.R;
import com.sspacee.common.util.LogUtil;

import com.qq.myapp.constant.Constants;
import com.qq.myapp.ui.fragment.AutoVideoFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by themass on 2015/9/1.
 */
public class VideoListShowActivity extends AppCompatActivity {
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
            AutoVideoFragment fragment = AutoVideoFragment.class.newInstance();
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

}
