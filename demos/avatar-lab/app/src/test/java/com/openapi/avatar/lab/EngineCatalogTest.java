package com.openapi.avatar.lab;

import org.junit.Test;
import static org.junit.Assert.*;

public class EngineCatalogTest {
    @Test public void catalogUrlsUseHttpsAndHaveAttribution() {
        for (EngineCatalog.Entry entry : EngineCatalog.ALL) {
            assertTrue(entry.id, EngineCatalog.isValid(entry));
            assertTrue(entry.id, entry.source.startsWith("https://"));
            assertTrue(entry.id, entry.url.startsWith("https://"));
        }
    }
    @Test public void onlineSamplesGalleriesAndReferencesRemainDistinct() {
        assertEquals(11, EngineCatalog.ALL.size());
        // 六个本地离线页，含原创机器人双引擎对比；在线只剩 gallery/reference。
        assertEquals(6, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.LOCAL).count());
        assertEquals(EngineCatalog.Kind.LOCAL, EngineCatalog.find("original-robot").kind);
        assertEquals(0, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.ONLINE_SAMPLE).count());
        assertEquals(1, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.ONLINE_GALLERY).count());
        assertEquals(0, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.ONLINE_THIRD_PARTY).count());
        assertEquals(4, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.REFERENCE).count());
        for (String id : new String[]{"duix", "tao", "inochi", "live2d-source"}) {
            assertEquals(EngineCatalog.Kind.REFERENCE, EngineCatalog.find(id).kind);
            assertTrue(EngineCatalog.find(id).status.contains("不计入运行验收"));
        }
        assertEquals(EngineCatalog.Kind.ONLINE_GALLERY, EngineCatalog.find("rive").kind);
        assertTrue(EngineCatalog.find("rive").status.contains("非运行时集成"));
    }
    @Test public void everyLocalEntryServesAnPackagedAssetPage() {
        for (EngineCatalog.Entry local : EngineCatalog.ALL) {
            if (local.kind != EngineCatalog.Kind.LOCAL) continue;
            assertTrue(local.id, EngineCatalog.isValid(local));
            // 每个本地页都必须落在 asset 源内、以 index.html 结尾,且带「本地集成」状态。
            assertTrue(local.id, local.url.startsWith("https://appassets.androidplatform.net/assets/"));
            assertTrue(local.id, local.url.endsWith("/index.html"));
            assertTrue(local.id, local.status.contains("本地集成"));
        }
    }
    @Test public void localEntryRejectsForeignOrigin() {
        EngineCatalog.Entry local = EngineCatalog.find("local-scene");
        assertTrue(EngineCatalog.isValid(local));
        EngineCatalog.Entry forged = new EngineCatalog.Entry(local.id, local.name, local.kind, local.status, local.description, local.tryList, local.source, "https://example.com/scene.html");
        assertFalse(EngineCatalog.isValid(forged));
        EngineCatalog.Entry offPath = new EngineCatalog.Entry(local.id, local.name, local.kind, local.status, local.description, local.tryList, local.source, "https://appassets.androidplatform.net/assets/evil.html");
        assertFalse(EngineCatalog.isValid(offPath));
    }
    @Test public void lookupFallsBackToLocalScene() {
        assertEquals("local-scene", EngineCatalog.find("missing").id);
    }
}
