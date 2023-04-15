package com.openapi.commons.common.util;

import android.content.Context;
import android.graphics.Bitmap;

import com.bumptech.glide.load.engine.Resource;

import java.security.MessageDigest;

import jp.wasabeef.glide.transformations.BlurTransformation;

/**
 * Created by openapi on 2017/9/5.
 */

public class MyBlurTransformation extends BlurTransformation {
    private static int MAX_RADIUS = 15;
    private static int DEFAULT_DOWN_SAMPLING = 1;

    public MyBlurTransformation(Context context) {
        this(context, MAX_RADIUS, DEFAULT_DOWN_SAMPLING);
    }

    public MyBlurTransformation(Context context, int radius, int sampling) {
        super(context, radius, sampling);
    }

    public Resource<Bitmap> transform(Context context, Resource<Bitmap> resource, int outWidth, int outHeight) {
        return super.transform(resource, outWidth, outHeight);
    }

    public int hashCode() {
        return getClass().getName().hashCode();
    }

    public void updateDiskCacheKey(MessageDigest messageDigest) {
        messageDigest.update(getClass().getName().getBytes(CHARSET));
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof MyBlurTransformation);
    }
}
