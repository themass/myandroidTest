package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Test

class Live2dSceneSetupTest {
    @Test
    fun `scene load applies base expression after selecting model before its first draw`() {
        val commands = mutableListOf<String>()
        prepareLive2dScene(
            modelPath = "/models/Amadeus/",
            modelJson = "Amadeus.model3.json",
            selectModel = { path, json -> commands += "model:$path$json" },
            enableBlink = { commands += "blink:$it" },
            applyExpression = { commands += "expression:$it" },
        )
        assertEquals(
            listOf("model:/models/Amadeus/Amadeus.model3.json", "blink:true", "expression:base"),
            commands,
        )
    }
}
