package com.openapi.app.fxlab

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RiveAssetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bundledRiveFixturesExistAndAreNonEmpty() {
        listOf("fxlab_loopy", "fxlab_circle_move").forEach { name ->
            val id = riveRawResFor(name)
            assertTrue("$name missing", id != 0)
            assertTrue(
                "$name empty",
                context.resources.openRawResource(id).use { it.readBytes().size > 100 },
            )
        }
    }
}
