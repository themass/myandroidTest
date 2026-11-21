package com.openapi.feature.content.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * 整改清单 P3-21：列表页「看过置灰」以前在每次 onBindViewHolder 里同步查一次 Room，
 * 主线程查询被关掉后那会直接崩。改成内存集合：后台一次性装填，bind 时只查内存。
 */
public class VisitedUrlCacheTest {

    @Test
    public void unknownUrlIsNotVisited() {
        VisitedUrlCache cache = new VisitedUrlCache();
        assertFalse(cache.isVisited("http://a/1"));
    }

    @Test
    public void loadedUrlsBecomeVisited() {
        VisitedUrlCache cache = new VisitedUrlCache();
        cache.loadFrom(Arrays.asList("http://a/1", "http://a/2"));
        assertTrue(cache.isVisited("http://a/1"));
        assertTrue(cache.isVisited("http://a/2"));
        assertFalse(cache.isVisited("http://a/3"));
    }

    /** 空列表也是一份完整的装载结果：没记录过就该是「没看过」，不能被旧数据顶回来。 */
    @Test
    public void loadReplacesPreviousContents() {
        VisitedUrlCache cache = new VisitedUrlCache();
        cache.markVisited("http://old");
        cache.loadFrom(Collections.emptyList());
        assertFalse(cache.isVisited("http://old"));
    }

    @Test
    public void markVisitedAddsImmediately() {
        VisitedUrlCache cache = new VisitedUrlCache();
        cache.markVisited("http://b/1");
        assertTrue(cache.isVisited("http://b/1"));
    }

    /** 地址为空（列表里偶发脏数据）不许把 null 当成已访问。 */
    @Test
    public void nullUrlIsNeverVisited() {
        VisitedUrlCache cache = new VisitedUrlCache();
        cache.markVisited(null);
        cache.loadFrom(Arrays.asList((String) null));
        assertFalse(cache.isVisited(null));
    }

    @Test
    public void markVisitedToleratesNullInsideLoadedList() {
        VisitedUrlCache cache = new VisitedUrlCache();
        cache.loadFrom(Arrays.asList("http://ok", null));
        assertTrue(cache.isVisited("http://ok"));
        assertFalse(cache.isVisited(null));
    }
}
