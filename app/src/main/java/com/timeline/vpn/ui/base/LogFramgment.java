package com.timeline.vpn.ui.base;

import android.animation.Animator;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.data.MobAgent;

/**
 * Created by gqli on 2016/3/16.
 */
public class LogFramgment extends Fragment{
    @Override
    public void onStart() {
        super.onStart();
        LogUtil.i(getClass().getSimpleName()+"-onStart");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LogUtil.i(getClass().getSimpleName()+"-onCreateView");
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LogUtil.i(getClass().getSimpleName()+"-onCreate");
        super.onCreate(savedInstanceState);
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        LogUtil.i(getClass().getSimpleName()+"-onCreateAnimator");
        return super.onCreateAnimator(transit, enter, nextAnim);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        LogUtil.i(getClass().getSimpleName() + "-onActivityCreated");
    }

    @Override
    public void onResume() {
        super.onResume();
        LogUtil.i(getClass().getSimpleName() + "-onResume");
        MobAgent.onPageStart(this.getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();
        LogUtil.i(getClass().getSimpleName() + "-onPause");
        MobAgent.onPageEnd(this.getActivity());
    }

    @Override
    public void onStop() {
        super.onStop();
        LogUtil.i(getClass().getSimpleName() + "-onStop");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LogUtil.i(getClass().getSimpleName() + "-onDestroyView");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.i(getClass().getSimpleName() + "-onDestroy");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        LogUtil.i(getClass().getSimpleName() + "-onDetach");
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        LogUtil.i(getClass().getSimpleName() + "-onAttach");
    }

    @Override
    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        super.onInflate(context, attrs, savedInstanceState);
        LogUtil.i(getClass().getSimpleName() + "-onInflate");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LogUtil.i(getClass().getSimpleName() + "-onViewCreated");
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        LogUtil.i(getClass().getSimpleName() + "-onSaveInstanceState");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LogUtil.i(getClass().getSimpleName() + "-onConfigurationChanged");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        LogUtil.i(getClass().getSimpleName() + "-onLowMemory");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        LogUtil.i(getClass().getSimpleName() + "-onCreateOptionsMenu");
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        LogUtil.i(getClass().getSimpleName() + "-onPrepareOptionsMenu");
    }

    @Override
    public void onDestroyOptionsMenu() {
        super.onDestroyOptionsMenu();
        LogUtil.i(getClass().getSimpleName() + "-onDestroyOptionsMenu");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        LogUtil.i(getClass().getSimpleName() + "-onOptionsItemSelected");
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        LogUtil.i(getClass().getSimpleName() + "-onOptionsMenuClosed");
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        LogUtil.i(getClass().getSimpleName() + "-onCreateContextMenu");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        LogUtil.i(getClass().getSimpleName() + "-onContextItemSelected");
        return super.onContextItemSelected(item);
    }

    @Override
    public void registerForContextMenu(View view) {
        super.registerForContextMenu(view);
        LogUtil.i(getClass().getSimpleName() + "-registerForContextMenu");
    }

    @Override
    public void unregisterForContextMenu(View view) {
        super.unregisterForContextMenu(view);
        LogUtil.i(getClass().getSimpleName() + "-unregisterForContextMenu");
    }
}
