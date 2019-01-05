package com.qq.vpn.ui.base.actvity;

import android.content.Intent;
import android.os.Bundle;

import com.qq.MobAgent;
import com.qq.ext.util.LogUtil;

/**
 * Created by dengt on 2016/3/22.
 */
public class LogActivity extends SlidingActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtil.i(getClass().getSimpleName() + "-onCreate");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStop() {
        LogUtil.i(getClass().getSimpleName() + "-onStop");
        super.onStop();
    }

    @Override
    protected void onPostResume() {
        LogUtil.i(getClass().getSimpleName() + "-onPostResume");
        super.onPostResume();
    }

    @Override
    protected void onDestroy() {
        LogUtil.i(getClass().getSimpleName() + "-onDestroy");
//        RefWatcher refWatcher = MyApplication.getRefWatcher(this);
//        refWatcher.watch(this);
        super.onDestroy();

    }

    @Override
    protected void onPause() {
        LogUtil.i(getClass().getSimpleName() + "-onPause");
        super.onPause();
        MobAgent.onPauseForFragmentActiviy(this);
    }

    @Override
    public void onLowMemory() {
        LogUtil.i(getClass().getSimpleName() + "-onLowMemory");
        super.onLowMemory();
    }

    @Override
    protected void onResume() {
        LogUtil.i(getClass().getSimpleName() + "-onResume");
        super.onResume();
        MobAgent.onResumeForFragmentActiviy(this);
    }

    @Override
    protected void onStart() {
        LogUtil.i(getClass().getSimpleName() + "-onStart");
        super.onStart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }
}