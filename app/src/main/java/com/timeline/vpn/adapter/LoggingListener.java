package com.timeline.vpn.adapter;

import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.sspacee.common.util.LogUtil;

public class LoggingListener<T, R> implements RequestListener<T, R> {
    @Override
    public boolean onException(Exception e, Object model, Target target, boolean isFirstResource) {
        LogUtil.e(String.format("GLIDE-onException(%s, %s, %s, %s)", e, model, target, isFirstResource), e);
        return false;
    }

    @Override
    public boolean onResourceReady(Object resource, Object model, Target target, boolean isFromMemoryCache, boolean isFirstResource) {
        LogUtil.i(String.format("GLIDE-onResourceReady(%s, %s, %s, %s, %s)", resource, model, target, isFromMemoryCache, isFirstResource));
        return false;
    }
}