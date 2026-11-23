package com.openapi.ks.myapp.dh

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DuixModelPathsTest {

    // AssetStore 在 DhLabBootstrap 里是进程内单例；Robolectric 每个测试的 filesDir 不同，必须清掉。
    @Before
    fun resetSharedAssetStore() {
        DhLabBootstrap.resetForTests()
    }

    @Test
    fun missing_base_config_reports_missing_model() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val status = DuixModelPaths.status(context, modelName = "demo_model")
        assertFalse(status.ready)
        assertEquals("未下载", status.reason)
    }

    @Test
    fun default_model_is_oliver_under_asset_store_cache() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Oliver", DuixModelPaths.DEFAULT_MODEL)
        assertEquals(
            DhLabBootstrap.assetStore(context).cacheRoot(com.openapi.ks.dh.EngineAssets.DUIX),
            DuixModelPaths.duixRoot(context),
        )
    }

    @Test
    fun ready_when_base_and_model_tags_exist() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = DuixModelPaths.duixRoot(context)
        File(root, "model/gj_dh_res").mkdirs()
        File(root, "model/tmp/gj_dh_res").mkdirs()
        File(root, "model/demo_model").mkdirs()
        File(root, "model/tmp/demo_model").mkdirs()
        val status = DuixModelPaths.status(context, modelName = "demo_model")
        assertTrue(status.ready)
    }
}
