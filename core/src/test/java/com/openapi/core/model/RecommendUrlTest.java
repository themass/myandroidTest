package com.openapi.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** 服务端常把 actionUrl/img 写成相对路径，再配 baseurl；客户端必须拼成可跳转/可加载的绝对地址。 */
public class RecommendUrlTest {

    @Test
    public void resolve_joinsRelativePathToBase() {
        assertEquals(
                "https://xiaoyakankan.com/cat/1023.html",
                RecommendUrl.resolve("https://xiaoyakankan.com", "/cat/1023.html"));
    }

    @Test
    public void resolve_joinsWhenBaseHasTrailingSlashAndPathHasNoLeading() {
        assertEquals(
                "https://xiaoyakankan.com/cover.jpg",
                RecommendUrl.resolve("https://xiaoyakankan.com/", "cover.jpg"));
    }

    @Test
    public void resolve_keepsAbsoluteUrl() {
        assertEquals(
                "https://file.ok123find.top/a.jpg",
                RecommendUrl.resolve("https://xiaoyakankan.com", "https://file.ok123find.top/a.jpg"));
    }

    @Test
    public void resolve_keepsCustomScheme() {
        assertEquals(
                "sound://config?channel=x",
                RecommendUrl.resolve("https://xiaoyakankan.com", "sound://config?channel=x"));
    }

    @Test
    public void resolve_returnsPathWhenBaseMissing() {
        assertEquals("/cat/1023.html", RecommendUrl.resolve(null, "/cat/1023.html"));
        assertEquals("/cat/1023.html", RecommendUrl.resolve("", "/cat/1023.html"));
    }

    @Test
    public void resolve_nullOrBlankPath() {
        assertNull(RecommendUrl.resolve("https://xiaoyakankan.com", null));
        assertEquals("", RecommendUrl.resolve("https://xiaoyakankan.com", ""));
    }
}
