package com.openapi.ks.dh.duix.sdk.client.render;

import com.openapi.ks.dh.duix.sdk.client.bean.ImageFrame;

/**
 * 渲染管道，通过该接口返回渲染数据
 */
public interface RenderSink {

    void onVideoFrame(ImageFrame imageFrame);

}
