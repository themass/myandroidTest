package com.openapi.feature.video.cast;

import androidx.annotation.Nullable;

/**
 * 当前详情页可投屏的片源（Google Cast 需设备拉取公网 URL；与 GSY 自定义 Header 无直接对应关系）。
 */
public interface CastPlaySource {

    /** 播放地址，需为 {@code http(s)://}；懒加载未完成时可返回 null。 */
    @Nullable
    String getPlayUrl();

    @Nullable
    String getTitle();
}
