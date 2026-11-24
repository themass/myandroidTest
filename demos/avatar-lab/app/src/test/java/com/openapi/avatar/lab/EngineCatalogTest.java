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
        assertEquals(1, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.LOCAL).count());
        assertEquals(4, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.ONLINE_SAMPLE).count());
        assertEquals(1, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.ONLINE_GALLERY).count());
        assertEquals(1, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.ONLINE_THIRD_PARTY).count());
        assertEquals(4, EngineCatalog.ALL.stream().filter(e -> e.kind == EngineCatalog.Kind.REFERENCE).count());
        for (String id : new String[]{"duix", "tao", "inochi", "live2d-source"}) {
            assertEquals(EngineCatalog.Kind.REFERENCE, EngineCatalog.find(id).kind);
            assertTrue(EngineCatalog.find(id).status.contains("不计入运行验收"));
        }
        assertEquals(EngineCatalog.Kind.ONLINE_THIRD_PARTY, EngineCatalog.find("live2d").kind);
        assertTrue(EngineCatalog.find("live2d").status.contains("非 Live2D 官方 demo"));
        assertTrue(EngineCatalog.find("inochi").status.contains("不计入运行验收"));
        assertTrue(EngineCatalog.find("rive").status.contains("非运行时集成"));
    }
    @Test public void localEntryIsRestrictedToAssetLoaderOrigin() {
        EngineCatalog.Entry local = EngineCatalog.find("local-scene");
        assertTrue(EngineCatalog.isValid(local));
        EngineCatalog.Entry forged = new EngineCatalog.Entry(local.id, local.name, local.kind, local.status, local.description, local.tryList, local.source, "https://example.com/scene.html");
        assertFalse(EngineCatalog.isValid(forged));
    }
    @Test public void lookupFallsBackToLocalScene() {
        assertEquals("local-scene", EngineCatalog.find("missing").id);
    }
}
