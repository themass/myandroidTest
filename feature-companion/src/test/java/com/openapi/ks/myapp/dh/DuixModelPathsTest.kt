package com.openapi.ks.myapp.dh

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DuixModelPathsTest {

    @Test
    fun missing_base_config_reports_missing_model() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val status = DuixModelPaths.status(context, modelName = "demo_model")
        assertFalse(status.ready)
        assertTrue(status.reason.contains("缺模型") || status.reason.contains("gj_dh_res"))
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
