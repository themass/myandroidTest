// Vendored from MnnTaoAvatar (com.taobao.meta.avatar.nnr.NnrAvatarRender), package renamed.
// Task 6 adaptations: dropped the DebugWriteBlendShape frame-dump path (audioBsToSave /
// doFrameDebug / getFirstAudioBlendShape) — the debug const is gone from this module and the
// A2BS player is an idle no-op stub.

package com.openapi.ks.dh.tao.nnr

import android.util.Log
import android.view.Surface
import com.openapi.ks.dh.tao.MHConfig
import com.openapi.ks.dh.tao.a2bs.AudioBlendShapePlayer
import com.openapi.ks.dh.tao.camera.CameraControlData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

class NnrAvatarRender(
    private val avatarTextureView: AvatarTextureView,
    private val modelDir: String,
) {
    private var nnrRuntimeNative: Long = 0
    private var surfaceCreated = false
    private var resourceLoaded = false
    private val firstInitComplete = CompletableDeferred<Boolean>()
    private var audioBlendShapePlayer: AudioBlendShapePlayer? = null
    private var initStarted = false
    private var activeSurface:Surface?= null

    private var hasDownloadComplete = false

    init {
        surfaceCreated = avatarTextureView.hasSurfaceCreated()
        avatarTextureView.nnrAvatarRender = this
        avatarTextureView.setSurfaceListener(object : AvatarTextureView.SurfaceListener {
            override fun surfaceCreated(surface: Surface) {
                Log.d(TAG, "surfaceCreated")
                surfaceCreated = true
                activeSurface = surface
                if (hasDownloadComplete) {
                    initRender(surface)
                }
            }

            override fun surfaceDestroyed(surface: Surface) {
                Log.d(TAG, "surfaceDestroyed")
                activeSurface = null
                destroyRender(surface)
            }
        })
    }

    private fun destroyRender(surface:Surface) {
        surfaceCreated = false
        resourceLoaded = false
        destroy()
    }

    private fun initRender(surface:Surface) {
        Log.d(TAG, "initRender modelDir: $modelDir")
        if (!File(modelDir).exists()) {
            Log.e(TAG, "modelDir not exists")
            return
        }
        initStarted = true
        nnrRuntimeNative = nativeCreateNNR()
        val cachePath = avatarTextureView.context.cacheDir.absolutePath + "/nnr_cache"
        File(cachePath).mkdirs()
        nativeInitNNR(nnrRuntimeNative, surface, modelDir,
            cachePath)
        CoroutineScope(Dispatchers.IO).launch {
            loadResources()
            if (!firstInitComplete.isCompleted) {
                firstInitComplete.complete(true)
            }
        }

    }

    fun setAudioBlendShapePlayer(audioBlendShapePlayer: AudioBlendShapePlayer) {
        this.audioBlendShapePlayer = audioBlendShapePlayer
    }

    suspend fun waitForInitComplete(): Boolean {
        hasDownloadComplete = true
        if (activeSurface != null && !initStarted) {
            initRender(activeSurface!!)
        }
        return firstInitComplete.await()
    }

    private suspend fun loadResources() {
        if (resourceLoaded) {
            return
        }
        CoroutineScope(Dispatchers.IO).async {
            Log.d(TAG, "loadResources")
            loadResourcesFromFile(
                "$modelDir/compute.nnr",
                "$modelDir/render_full.nnr",
                "$modelDir/background.nnr",
                "$modelDir/input_nnr.json"
            )
            Log.d(TAG, "loadResources success")
        }.await()
        resourceLoaded = true
    }

    private fun render() {
        nativeRender(nnrRuntimeNative)
    }

    private fun destroy() {
        if (nnrRuntimeNative != 0L) {
            nativeDestroy(nnrRuntimeNative)
        }
        nnrRuntimeNative = 0
    }

    private val isNNRReady: Boolean
        get() = nativeIsNNRReady(nnrRuntimeNative)

    private fun updateNNRScene(cameraControl: CameraControlData,
                               isPlaying:Boolean,
                               currentTimeMills: Long,
                               totalTimeMills: Long,
                               isBuffering:Boolean,
                               smoothToIdlePercent: Float,
                               smoothToTalkPercent: Float,
                               forceFrameIndex: Long) {

        nativeUpdateNNRScene(nnrRuntimeNative,
            cameraControl,
            isPlaying,
            currentTimeMills,
            totalTimeMills,
            isBuffering,
            smoothToIdlePercent,
            smoothToTalkPercent,
            forceFrameIndex)
    }

    fun reset() {
        avatarTextureView.reset()
        nativeReset(nnrRuntimeNative)
    }

    private fun loadResourcesFromFile(
        computeSceneFileName: String,
        renderSceneFileName: String,
        skyboxSceneFileName: String,
        deformParamFileName: String
    ): Boolean {
        return nativeLoadNnrResources(
            nnrRuntimeNative,
            MHConfig.A2BS_MODEL_DIR,
            computeSceneFileName,
            renderSceneFileName,
            skyboxSceneFileName,
            deformParamFileName,
            "${MHConfig.A2BS_MODEL_DIR}/idle_speech_slices.json"
        )
    }

    fun doFrame():Boolean {
        if (isNNRReady) {
            val startTime = System.currentTimeMillis()
            val playingStatus = audioBlendShapePlayer?.update()
//            Log.v(TAG, "isPlaying: ${audioBlendShapePlayer?.isPlaying} " +
//                    ".currentTimeMills ${audioBlendShapePlayer?.currentTime} "  +
//                    "totalTimeMills: ${audioBlendShapePlayer?.totalTime} " +
//                    "isBuffering: ${audioBlendShapePlayer?.isBuffering} " +
//                        "position ${audioBlendShapePlayer?.currentHeadPosition}  " +
//                "currentPlayingText: ${audioBlendShapePlayer?.currentPlayingText}"
//            )
            this.updateNNRScene(avatarTextureView.cameraCtrlData!!,
                audioBlendShapePlayer?.isPlaying?:false,
                audioBlendShapePlayer?.currentTime?:0L,
                audioBlendShapePlayer?.totalTime?:0L,
                audioBlendShapePlayer?.isBuffering?:false,
                playingStatus?.smoothToIdlePercent?:-1f,
                playingStatus?.smoothToTalkPercent?:-1f,
                -1)
            this.render()
//            Log.v(TAG, "updateNNRScene cost ${System.currentTimeMillis() - startTime} ms")
            return true
        }
        return false
    }

    companion object {

        const val TAG = "NnrAvatarRender"

        @JvmStatic
        private external fun nativeCreateNNR(): Long

        @JvmStatic
        private external fun nativeInitNNR(nativePtr: Long, surface: Surface, modelDir: String, cacheDir: String)

        @JvmStatic
        private external fun nativeDestroy(nativePtr: Long)

        @JvmStatic
        private external fun nativeRender(nativePtr: Long)

        @JvmStatic
        private external fun nativeReset(nativePtr: Long)

        @JvmStatic
        private external fun nativeIsNNRReady(nativePtr: Long): Boolean

        @JvmStatic
        private external fun nativeUpdateNNRScene(
            nativePtr: Long,
            cameraControl: CameraControlData,
            isPlaying: Boolean,
            timeInMilliseconds: Long,
            totalTimeMills: Long,
            isBuffering: Boolean,
            smoothToIdlePercent: Float,
            smoothToTalkPercent: Float,
            forceFrameIndex: Long
        )

        @JvmStatic
        private external fun nativeLoadNnrResources(
            nativePtr: Long,
            modelDir: String,
            computeSceneFileName: String,
            renderSceneFileName: String,
            skyboxSceneFileName: String,
            deformParamFileName: String,
            chatStatusFileName:String
        ): Boolean
    }
}
