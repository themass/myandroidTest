package com.timeline.vpn.ui.base;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.timeline.vpn.common.util.LogUtil;

/**
 * Created by gqli on 2016/3/22.
 */
public class LogActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtil.i(getClass().getSimpleName()+"-onCreate");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStop() {
        LogUtil.i(getClass().getSimpleName()+"-onStop");
        super.onStop();
    }

    @Override
    protected void onPostResume() {
        LogUtil.i(getClass().getSimpleName()+"-onPostResume");
        super.onPostResume();
    }

    @Override
    protected void onDestroy() {
        LogUtil.i(getClass().getSimpleName()+"-onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        LogUtil.i(getClass().getSimpleName()+"-onPause");
        super.onPause();
    }

    @Override
    public void onLowMemory() {
        LogUtil.i(getClass().getSimpleName()+"-onLowMemory");
        super.onLowMemory();
    }

    @Override
    protected void onResume() {
        LogUtil.i(getClass().getSimpleName()+"-onResume");
        super.onResume();
    }

    @Override
    protected void onStart() {
        LogUtil.i(getClass().getSimpleName()+"-onStart");
        super.onStart();
    }
    @Override
    protected void onNewIntent(Intent intent) {
        LogUtil.i(getClass().getSimpleName()+"-onNewIntent");
        super.onNewIntent(intent);
    }

}
