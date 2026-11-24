package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Test

class StickerSlotStateTest {

    @Test
    fun `reacquisition after held snaps to current face placement`() {
        val slot = StickerSlotState()
        val first = StickerPlacementPx(100f, 100f, 200f, 0f)
        val reacquired = StickerPlacementPx(120f, 100f, 200f, 0f)

        assertEquals(first, slot.anchor(first))
        assertEquals(first, slot.hold())

        // If the held state forgot to invalidate lastWasAnchored, this would be 107px
        // (EMA alpha 0.35) instead of snapping to the new observation at 120px.
        assertEquals(reacquired, slot.anchor(reacquired))
    }

    @Test
    fun `consecutive anchors are smoothed`() {
        val slot = StickerSlotState()
        slot.anchor(StickerPlacementPx(100f, 100f, 200f, 0f))
        val smoothed = slot.anchor(StickerPlacementPx(110f, 100f, 200f, 0f))

        assertEquals(103.5f, smoothed.centerX, 0.01f)
    }
}
