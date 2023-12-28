package com.openapi.ks.myapp.ui.sound;


import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.widget.FrameLayout;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.PreferenceUtils;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.myapp.ui.fragment.AutoVideoListFragment;
import com.openapi.ks.myapp.ui.gsy.GSYAutoVideoListFragment;

import java.util.HashMap;

import butterknife.ButterKnife;
import butterknife.Unbinder;
/**
 * Created by openapi on 2015/9/1.
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
        try {
            BasePullLoadbleFragment fragment;
            boolean needTiny = PreferenceUtils.getPrefBoolean(this, Constants.LISTVIDEO_TINY_SWITCH, true);
            if(!needTiny){
                fragment = AutoVideoListFragment.class.newInstance();
            }else{

                fragment = GSYAutoVideoListFragment.class.newInstance();
            }
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
