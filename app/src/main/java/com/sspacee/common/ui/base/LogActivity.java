package com.sspacee.common.ui.base;

import android.content.Intent;
import android.os.Bundle;

import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.BaseAdsController;

/**
 * Created by themass on 2016/3/22.
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
        BaseAdsController.onStop(this);
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
        BaseAdsController.onDestroy(this);
        super.onDestroy();

    }

    @Override
    protected void onPause() {
        LogUtil.i(getClass().getSimpleName() + "-onPause");
        super.onPause();
        BaseAdsController.onPause(this);
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
        BaseAdsController.onResume(this);
    }

    @Override
    protected void onStart() {
        LogUtil.i(getClass().getSimpleName() + "-onStart");
        super.onStart();
        BaseAdsController.onStart(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }

}
