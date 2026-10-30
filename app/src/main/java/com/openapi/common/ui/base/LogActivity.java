package com.openapi.common.ui.base;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.openapi.common.ui.base.EdgeToEdgeHelper;
import com.openapi.common.util.LogUtil;

/**
 * Created by dengt on 2016/3/22.
 */
public class LogActivity extends com.openapi.common.ui.base.SlidingActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        LogUtil.i(getClass().getSimpleName() + "-onCreate");
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        View content = findViewById(android.R.id.content);
        if (content instanceof android.view.ViewGroup && ((android.view.ViewGroup) content).getChildCount() > 0) {
            EdgeToEdgeHelper.applyRootPadding(((android.view.ViewGroup) content).getChildAt(0));
        }
    }

    @Override
    protected void onStop() {
//        LogUtil.i(getClass().getSimpleName() + "-onStop");
        super.onStop();
    }

    @Override
    protected void onPostResume() {
//        LogUtil.i(getClass().getSimpleName() + "-onPostResume");
        super.onPostResume();
    }

    @Override
    protected void onDestroy() {
//        LogUtil.i(getClass().getSimpleName() + "-onDestroy");
//        RefWatcher refWatcher = MyApplication.getRefWatcher(this);
//        refWatcher.watch(this);
        super.onDestroy();

    }

    @Override
    protected void onPause() {
//        LogUtil.i(getClass().getSimpleName() + "-onPause");
        super.onPause();
    }

    @Override
    public void onLowMemory() {
//        LogUtil.i(getClass().getSimpleName() + "-onLowMemory");
        super.onLowMemory();
    }

    @Override
    protected void onResume() {
//        LogUtil.i(getClass().getSimpleName() + "-onResume");
        super.onResume();
    }

    @Override
    protected void onStart() {
//        LogUtil.i(getClass().getSimpleName() + "-onStart");
        super.onStart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
//        LogUtil.i(getClass().getSimpleName() + "-onNewIntent");
        super.onNewIntent(intent);
    }

}
