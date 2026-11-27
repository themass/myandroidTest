package com.openapi.ks.myapp.dh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DhLabPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DhLabPreferences.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun prefs_default_simple() {
        val prefs = DhLabPreferences(context)
        assertEquals(DhEngineId.SIMPLE, prefs.engine)
        assertFalse(prefs.labForced)
    }

    @Test
    fun prefs_persist_engine() {
        val prefs = DhLabPreferences(context)
        prefs.engine = DhEngineId.DUIX
        assertEquals(DhEngineId.DUIX, DhLabPreferences(context).engine)
    }

    @Test
    fun prefs_lab_forced() {
        val prefs = DhLabPreferences(context)
        prefs.labForced = true
        assertTrue(DhLabPreferences(context).labForced)
    }

    @Test
    fun call_stage_choice_defaults_to_unset_and_keeps_the_three_states_apart() {
        // 没存过 = Unset（服务端 her 可以决定引擎）；三态必须互不塌缩。
        assertEquals(StageChoice.Unset, DhLabPreferences(context).callStageChoice)
        DhLabPreferences(context).callStageChoice = StageChoice.Original
        assertEquals(StageChoice.Original, DhLabPreferences(context).callStageChoice)
        DhLabPreferences(context).callStageChoice = StageChoice.Engine(DhEngineId.LIVE2D)
        assertEquals(StageChoice.Engine(DhEngineId.LIVE2D), DhLabPreferences(context).callStageChoice)
    }

    @Test
    fun call_stage_choice_reads_the_legacy_stored_values() {
        // 旧版直接往键里写：引擎名 = 选过该引擎，"ORIGINAL" = 显式原形象，缺失 = 没选过。
        // 升级后这两类老值不能被抹成同一种（显式原形象会被服务端覆盖 = bug）。
        val raw = context.getSharedPreferences(DhLabPreferences.PREFS, Context.MODE_PRIVATE)
        raw.edit().putString("companion.dh_call_stage", "LIVE2D").commit()
        assertEquals(StageChoice.Engine(DhEngineId.LIVE2D), DhLabPreferences(context).callStageChoice)
        raw.edit().putString("companion.dh_call_stage", "ORIGINAL").commit()
        assertEquals(StageChoice.Original, DhLabPreferences(context).callStageChoice)
        // 脏值（不认识的名字）当作显式原形象：保守，不让脏数据把用户推到服务端驱动的引擎上。
        raw.edit().putString("companion.dh_call_stage", "NOT_AN_ENGINE").commit()
        assertEquals(StageChoice.Original, DhLabPreferences(context).callStageChoice)
    }
}
