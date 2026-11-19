// Created by ruoyi.sjd on 2025/3/26.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
//
// Vendored from MnnTaoAvatar (com.taobao.meta.avatar.nnr.AvatarTextureView), package renamed.
// Task 6 adaptations: dropped the MainActivity cast and the debug PixelCopy / blend-shape
// frame-dump path (upstream FileUtils.saveBitmapToFile + HandlerThread copy machinery) — the
// dump was gated by MHConfig.DebugConfig.DebugWriteBlendShape, which is gone from this module.

package com.openapi.ks.dh.tao.nnr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.Choreographer.FrameCallback
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import com.openapi.ks.dh.tao.camera.CameraControlData
import kotlin.math.sqrt

class AvatarTextureView(context: Context?, attrs: AttributeSet?) :
    TextureView(context!!, attrs), TextureView.SurfaceTextureListener, FrameCallback {
    private var placeHolder: View? = null
    private var listener: SurfaceListener? = null
    var nnrAvatarRender: NnrAvatarRender? = null
    var cameraCtrlData: CameraControlData? = null
    private var lastX = 0.0f
    private var lastY = 0.0f
    private var fps: Long = 0
    private var sec: Long = 0
    private var hasSurfaceCreated:Boolean = false
    private var hasRendered = false
    private var supportScale = false
    private var enableGestures_ = false
    private var surface: Surface? = null

    private val incrementalRotationMatrix = FloatArray(16)
    private val translateToOriginMatrix = FloatArray(16)
    private val translateBackMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    private val meshCenterX = 0.0f
    private val meshCenterY = 0.0f
    private val meshCenterZ = -0.1f

    init {
        surfaceTextureListener = this
    }

    var enableGestures:Boolean
        get() = enableGestures_
        set(value) {
            enableGestures_ = value
        }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        hasRendered = false
        Log.d(TAG, "onSurfaceTextureAvailable")
        Choreographer.getInstance().postFrameCallback(this)
        cameraCtrlData = CameraControlData()
        hasSurfaceCreated = true
        surface = Surface(surfaceTexture)
        this.listener?.surfaceCreated(surface!!)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
    }

    // Upstream's body was the debug PixelCopy frame-dump (DebugWriteBlendShape-gated); stripped in Task 6.
    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Log.d(TAG, "onSurfaceTextureDestroyed")
        placeHolder?.visibility = View.VISIBLE
        Choreographer.getInstance().removeFrameCallback(this)
        cameraCtrlData = null
        hasSurfaceCreated = false
        surface?.let {
            this.listener?.surfaceDestroyed(it)
            it.release()
            surface = null
        }
        return true
    }

    fun setPlaceHolderView(view: View) {
        this.placeHolder = view
    }

    override fun doFrame(frameTimeNanos: Long) {
        val t = frameTimeNanos.toDouble() / 1000.0f / 1000.0f / 1000.0f
        if (t.toLong() > sec) {
            fps = 0
        }
        sec = t.toLong()
        fps++
        val rendered = nnrAvatarRender?.doFrame()
        if (rendered == true) {
            placeHolder?.visibility = View.GONE
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enableGestures_) {
            return false
        }
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {}
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                val dx = event.x - lastX
                val dy = event.y - lastY
                val angleY = Math.toDegrees((dx * cameraCtrlData!!.rotateSpeed).toDouble()).toFloat()
                Matrix.setIdentityM(incrementalRotationMatrix, 0)
                Matrix.rotateM(incrementalRotationMatrix, 0, angleY, 0.0f, 1.0f, 0.0f)
                Matrix.setIdentityM(translateToOriginMatrix, 0)
                Matrix.translateM(translateToOriginMatrix, 0, -meshCenterX, -meshCenterY, -meshCenterZ)
                Matrix.setIdentityM(translateBackMatrix, 0)
                Matrix.translateM(translateBackMatrix, 0, meshCenterX, meshCenterY, meshCenterZ)
                Matrix.multiplyMM(tempMatrix, 0, incrementalRotationMatrix, 0, translateToOriginMatrix, 0)
                Matrix.multiplyMM(incrementalRotationMatrix, 0, translateBackMatrix, 0, tempMatrix, 0)
                System.arraycopy(cameraCtrlData!!.modelMatrix, 0, tempMatrix, 0, 16)
                Matrix.multiplyMM(cameraCtrlData!!.modelMatrix, 0, tempMatrix, 0, incrementalRotationMatrix, 0)
                lastX = event.x
                lastY = event.y
            } else if (event.pointerCount == 2 && supportScale ) {
                val newDist = distance(event)
                if (newDist > TOUCH_DISTANCE_THRESHOLD) {
                    var scale = newDist / (cameraCtrlData!!.distanceOnScreen + 1e-7f)
                    scale *= cameraCtrlData!!.lastScale
                    cameraCtrlData!!.curScale = scale
                    if (cameraCtrlData!!.curScale > 50.0f) {
                        cameraCtrlData!!.curScale = 50.0f
                    } else if (cameraCtrlData!!.curScale < 0.01f) {
                        cameraCtrlData!!.curScale = 0.1f
                    }
                    val scaleInvMatrix = FloatArray(16)
                    Matrix.invertM(scaleInvMatrix, 0, cameraCtrlData!!.scaleMatrix, 0)
                    Matrix.scaleM(
                        cameraCtrlData!!.scaleMatrix,
                        0,
                        cameraCtrlData!!.curScale,
                        cameraCtrlData!!.curScale,
                        cameraCtrlData!!.curScale
                    )

                    Matrix.multiplyMM(
                        cameraCtrlData!!.modelMatrix,
                        0,
                        scaleInvMatrix,
                        0,
                        cameraCtrlData!!.modelMatrix,
                        0
                    )
                    Matrix.multiplyMM(
                        cameraCtrlData!!.modelMatrix,
                        0,
                        cameraCtrlData!!.scaleMatrix,
                        0,
                        cameraCtrlData!!.modelMatrix,
                        0
                    )
                    cameraCtrlData!!.distanceOnScreen = newDist
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) {
                cameraCtrlData!!.distanceOnScreen = distance(event)
            }

            MotionEvent.ACTION_POINTER_UP -> cameraCtrlData!!.lastScale =
                cameraCtrlData!!.curScale
        }
        return true
    }

    private fun distance(event: MotionEvent): Float {
        val deltaX = event.getX(0) - event.getX(1)
        val deltaY = event.getY(0) - event.getY(1)
        return sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
    }

    fun hasSurfaceCreated(): Boolean {
        return hasSurfaceCreated
    }

    fun setSurfaceListener(listener: SurfaceListener) {
        this.listener = listener
    }

    fun reset() {
        cameraCtrlData = CameraControlData()
    }

    interface SurfaceListener {
        fun surfaceCreated(surface: Surface)
        fun surfaceDestroyed(surface: Surface)
    }

    companion object {
        private const val TAG = "AvatarTextureView"
        private const val TOUCH_DISTANCE_THRESHOLD = 10.0f
    }
}
