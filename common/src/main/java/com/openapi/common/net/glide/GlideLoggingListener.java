package com.openapi.common.net.glide;

import androidx.annotation.Nullable;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.openapi.common.util.log.LogUtil;

/** 图片加载失败时打日志；不拦截结果（两个回调都返回 false）。 */
public class GlideLoggingListener<T> implements RequestListener<T> {

    @Override
    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<T> target, boolean isFirstResource) {
        LogUtil.e(String.format("GLIDE-onException(%s, %s, %s, %s)", e, model, target, isFirstResource), e);
        return false;
    }

    @Override
    public boolean onResourceReady(T resource, Object model, Target<T> target, DataSource dataSource, boolean isFirstResource) {
        return false;
    }
}
