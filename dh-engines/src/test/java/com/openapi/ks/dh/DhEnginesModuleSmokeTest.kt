package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Test

class DhEnginesModuleSmokeTest {

    @Test
    fun module_namespace_constant() {
        assertEquals("com.openapi.ks.dh", DhEngines.NAMESPACE)
    }
}
