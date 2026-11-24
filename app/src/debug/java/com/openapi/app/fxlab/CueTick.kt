package com.openapi.app.fxlab

/**
 * 舞台 cue 的「触发印章」：同一条手势连发也能让渲染层重播。
 *
 * [fire] 递增 [seq]；渲染层（LottieFxLayer 等）以 `gestureId + gestureSeq` 为 key，
 * seq 每次递增都是一次新触发（StageEffect.Play 每播一遍）。[stop] 只清 id、不递增
 * seq——「没有新触发」，渲染层看到手势已停但不会重播。
 */
class CueTick {
    var gestureId: String? = null
    var seq: Long = 0L

    fun fire(cue: String) {
        gestureId = cue
        seq += 1
    }

    fun stop() {
        gestureId = null
    }
}

/** 教学模式第 [index] 步的 cue。 */
fun teachCueFor(index: Int): String = FxTeachingScript.steps[index].cue