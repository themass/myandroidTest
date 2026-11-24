package com.openapi.app.fxlab

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreeWebViewUrlTest {

    @Test
    fun onlyWhitelistedPathsResolve() {
        assertEquals("three.html", FxLabThreeUrls.pathFor("https://fx-lab.local/three.html"))
        assertEquals("three.bundle.js", FxLabThreeUrls.pathFor("https://fx-lab.local/three.bundle.js"))
        assertEquals(null, FxLabThreeUrls.pathFor("https://evil.example/x.js"))
    }

    @Test
    fun otherPathsOnOurFakeOriginAreStillBlocked() {
        assertEquals(null, FxLabThreeUrls.pathFor("https://fx-lab.local/etc/passwd"))
        assertEquals(null, FxLabThreeUrls.pathFor("https://fx-lab.local/"))
    }

    @Test
    fun traversalInTheWhitelistedNameDoesNotEscape() {
        assertEquals(null, FxLabThreeUrls.pathFor("https://fx-lab.local/three.html/../../main.js"))
    }
}
