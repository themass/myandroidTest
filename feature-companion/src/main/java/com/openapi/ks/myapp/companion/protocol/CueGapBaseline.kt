package com.openapi.ks.myapp.companion.protocol

/**
 * Bridges lifetime [ProtocolReceiver.State.gaps] into a per-connection streak delta.
 *
 * `gaps` is a lifetime counter (forgetAll does not clear it). On reconnect the
 * transport must [baseline] to the current value; zeroing would replay history
 * into cueGapStreak and false-fire L1.
 */
class CueGapBaseline {
    private var lastObserved = 0

    /** Call on RoomEvent.Reconnected after protocolLink.onReconnected(). */
    fun baseline(gaps: Int) {
        lastObserved = gaps
    }

    /**
     * @return how many new gaps since last observe/baseline (0 if none).
     */
    fun observe(gapsNow: Int): Int {
        val d = delta(lastObserved, gapsNow)
        lastObserved = gapsNow
        return d
    }

    companion object {
        fun delta(lastObserved: Int, gapsNow: Int): Int =
            (gapsNow - lastObserved).coerceAtLeast(0)
    }
}
