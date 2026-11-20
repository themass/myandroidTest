package com.taobao.meta.avatar.a2bs

/**
 * JNI data holder mirror — the upstream FQN is load-bearing: libtaoavatar.so's
 * `nativeProcessBuffer` FindClass-es `com/taobao/meta/avatar/a2bs/AudioToBlendShapeData`
 * and GetField-ID-s every field below (names + `Ljava/util/List;` / `I` descriptors).
 * Keep in lockstep with upstream
 * `apps/Android/MnnTaoAvatar/app/src/main/java/com/taobao/meta/avatar/a2bs/AudioToBlendShapeData.kt`.
 */
class AudioToBlendShapeData {
    var expr: List<FloatArray> = ArrayList()
    var pose: List<FloatArray> = ArrayList()
    var pose_z: List<FloatArray> = ArrayList()
    var app_pose_z: List<FloatArray> = ArrayList()
    var joints_transform: List<FloatArray> = ArrayList()
    var jaw_pose: List<FloatArray> = ArrayList()
    var frame_num: Int = 0
}
