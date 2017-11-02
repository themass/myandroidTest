package com.timeline.vpn.ui.user;

import android.content.Context;
import android.util.AttributeSet;

import com.sspacee.common.util.LogUtil;

import cn.jzvd.JZVideoPlayerStandard;

/**
 * Created by themass on 2017/11/1.
 */

public class MyJZVideoPlayerStandard extends JZVideoPlayerStandard {
    public MyJZVideoPlayerStandard(Context context) {
        super(context);

    }

    public MyJZVideoPlayerStandard(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onInfo(int what, int extra) {
        super.onInfo(what, extra);
    }

    @Override
    public void startWindowFullscreen() {
        super.startWindowFullscreen();
        LogUtil.i("startWindowFullscreen");
    }

    @Override
    public void onStatePreparing() {
        super.onStatePreparing();
    }

    @Override
    public void setBufferProgress(int bufferProgress) {
        super.setBufferProgress(bufferProgress);
    }
}
