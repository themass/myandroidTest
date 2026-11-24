package com.openapi.app.fxlab

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class EffectsLabScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun statusLineNamesEngineDependencyAndGateReason() {
        val s = labStatusLine(
            FxEngineCatalog.meta.getValue(FxEngineId.AGSL_SHADER),
            GateOutcome.Unavailable("本机 API 28，RuntimeShader 需 API 33"),
            FxScene.CAMERA_FULL,
        )
        assertTrue(s.contains("AGSL") && s.contains("API 33"))
    }

    @Test
    fun engineChipSwitchUpdatesGateWithoutChangingScene() {
        var observed: LabUiState? = null
        compose.setContent {
            EffectsLabScreen(
                onStateChange = { observed = it },
            )
        }
        compose.waitForIdle()
        val before = requireNotNull(observed)
        compose.onNodeWithTag("fx_engine_RIVE").performClick()
        compose.waitForIdle()
        val after = requireNotNull(observed)
        assertEquals(FxEngineId.RIVE, after.engine)
        assertEquals(before.scene, after.scene)
    }

    @Test
    fun teachNextClampsAtEnds() {
        var observed: LabUiState? = null
        compose.setContent {
            EffectsLabScreen(onStateChange = { observed = it })
        }
        compose.onNodeWithTag("fx_scene_DH_TEACH").performClick()
        compose.onNodeWithTag("fx_teach_next").performClick()
        compose.onNodeWithTag("fx_teach_next").performClick()
        compose.onNodeWithTag("fx_teach_next").performClick()
        compose.onNodeWithTag("fx_teach_next").performClick()
        compose.onNodeWithTag("fx_teach_next").performClick()
        compose.waitForIdle()
        assertEquals(3, requireNotNull(observed).teachIndex)
    }
}
