package com.openapi.app.fxlab

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES30
import android.view.Surface
import android.view.TextureView
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.feature.camerafx.StickerPlacementPx
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
fun GLParticleOverlay(kind: ParticleKind, tick: Long, modifier: Modifier) {
    var error by remember(kind, tick) { mutableStateOf<String?>(null) }
    key(kind, tick) {
        if (error != null) Text("GLES3 不可用：$error", modifier, color = Color.Red)
        else AndroidView(
            factory = { context -> ParticleTexture(context, kind, tick) { error = it } },
            modifier = modifier,
            onRelease = { it.release() },
        )
    }
}

@Composable
fun GLParticleStickerRenderer(placement: StickerPlacementPx, modifier: Modifier) {
    GLParticleOverlay(ParticleKind.AMBIENT_LOOP, 0L, modifier)
}

/** EGL and all GL calls belong to one worker. SurfaceTexture ownership transfers to it. */
private class ParticleTexture(
    context: Context,
    private val kind: ParticleKind,
    private val tick: Long,
    private val reportError: (String) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var worker: ParticleThread? = null
    init { isOpaque = false; surfaceTextureListener = this }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        worker = ParticleThread(texture, width, height, kind, tick) { message ->
            post { reportError(message) }
        }.also { it.start() }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        worker?.widthPx = width; worker?.heightPx = height
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return false // worker releases texture after eglDestroySurface, never while GL uses it
    }
    fun release() { worker?.running = false; worker = null }
    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }
}

private class ParticleThread(
    private val texture: SurfaceTexture,
    @Volatile var widthPx: Int,
    @Volatile var heightPx: Int,
    private val kind: ParticleKind,
    private val tick: Long,
    private val reportError: (String) -> Unit,
) : Thread("fxlab-gles") {
    @Volatile var running = true
    override fun run() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        var context = EGL14.EGL_NO_CONTEXT
        var eglSurface = EGL14.EGL_NO_SURFACE
        val window = Surface(texture)
        var program = 0
        try {
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) { "EGL initialize" }
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE,
            ), 0, configs, 0, 1, count, 0) && count[0] > 0) { "No GLES3 RGBA config" }
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "GLES3 context" }
            eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], window,
                intArrayOf(EGL14.EGL_NONE), 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE &&
                EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "EGL surface" }
            program = createProgram()
            GLES30.glUseProgram(program)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            val vbo = IntArray(1)
            GLES30.glGenBuffers(1, vbo, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
            val buffer = ByteBuffer.allocateDirect(512 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val sim = FxParticleSim(kind, tick)
            while (running) {
                val frame = sim.step(1f / 60f)
                GLES30.glViewport(0, 0, widthPx, heightPx)
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                buffer.clear(); buffer.put(frame.positions); buffer.flip()
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, frame.count * 8, buffer, GLES30.GL_DYNAMIC_DRAW)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "size"),
                    (minOf(widthPx, heightPx) * 0.035f).coerceIn(3f, 22f))
                GLES30.glDrawArrays(GLES30.GL_POINTS, 0, frame.count)
                check(EGL14.eglSwapBuffers(display, eglSurface)) { "EGL swap" }
                if (!sim.alive()) break // final transparent frame already presented
                Thread.sleep(16)
            }
            GLES30.glDeleteBuffers(1, vbo, 0)
        } catch (e: Exception) {
            if (running) reportError(e.message ?: e.javaClass.simpleName)
        } finally {
            if (program != 0) GLES30.glDeleteProgram(program)
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(display)
            }
            window.release(); texture.release()
        }
    }

    private fun createProgram(): Int {
        fun shader(type: Int, source: String): Int {
            val id = GLES30.glCreateShader(type)
            GLES30.glShaderSource(id, source); GLES30.glCompileShader(id)
            val status = IntArray(1); GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val message = GLES30.glGetShaderInfoLog(id); GLES30.glDeleteShader(id)
                error(message)
            }
            return id
        }
        val vertex = shader(GLES30.GL_VERTEX_SHADER, """#version 300 es
            layout(location=0) in vec2 position;
            uniform float size;
            void main() { gl_Position=vec4(position.x*2.0-1.0,1.0-position.y*2.0,0.0,1.0); gl_PointSize=size; }
        """.trimIndent())
        val fragment = shader(GLES30.GL_FRAGMENT_SHADER, """#version 300 es
            precision mediump float;
            out vec4 color;
            void main() { float a=1.0-smoothstep(0.2,0.5,length(gl_PointCoord-vec2(0.5)));
                color=vec4(vec3(0.2,0.85,1.0)*a,a); }
        """.trimIndent())
        val id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vertex); GLES30.glAttachShader(id, fragment); GLES30.glLinkProgram(id)
        GLES30.glDeleteShader(vertex); GLES30.glDeleteShader(fragment)
        val status = IntArray(1); GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) { val message = GLES30.glGetProgramInfoLog(id); GLES30.glDeleteProgram(id); error(message) }
        return id
    }
}
