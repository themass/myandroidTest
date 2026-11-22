/**
 * Upstream-FQN JNI mirror for the stock `libchatwaifu-live2d.so` (same ruling as
 * ai.guiji.duix.DuixNcnn for Duix): the vendored Java under com.openapi.ks.dh.live2d keeps
 * our package, the prebuilt .so keeps its exported symbols
 * `Java_com_chatwaifu_live2d_JniBridgeJava_*`.
 *
 * Unlike the Duix mirror this class is stricter than a symbol carrier: the .so's JNI_OnLoad
 * does FindClass("com/chatwaifu/live2d/JniBridgeJava") and GetStaticMethodID for
 * LoadFile / MoveTaskToBack / OnLoadError / OnLoadDone / OnLoadOneMotion /
 * OnLoadOneExpression before the load returns. Removing or renaming anything here breaks
 * System.load itself — Live2dNativeBridgeTest pins the surface.
 *
 * No System.loadLibrary block: the library is dlopen'd by absolute path from the AssetStore
 * cache via com.openapi.ks.dh.Live2dNativeLoader, and Live2dRuntime gates availability on it.
 *
 * Copyright(c) Live2D Inc. / ChatWaifu — structure follows upstream JniBridgeJava.
 */

package com.chatwaifu.live2d;

import android.app.Activity;
import android.content.Context;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JniBridgeJava {

    private static Activity _activityInstance;
    private static Context _context;
    private static Callback _callback;

    // Native (exported by the stock .so under this exact FQN) -----------------

    public static native void nativeOnStart();

    public static native void nativeOnPause();

    public static native void nativeOnStop();

    public static native void nativeOnDestroy();

    public static native void nativeOnSurfaceCreated();

    public static native void nativeOnSurfaceChanged(int width, int height);

    public static native void nativeOnDrawFrame();

    public static native void nativeOnTouchesBegan(float pointX, float pointY);

    public static native void nativeOnTouchesEnded(float pointX, float pointY);

    public static native void nativeOnTouchesMoved(float pointX, float pointY);

    public static native void nativeProjectChangeTo(String modelPath, String modelJsonFileName);

    public static native void nativeApplyExpression(String expressionName);

    public static native void needRenderBack(boolean back);

    public static native void nativeProjectScale(float scale);

    public static native void nativeProjectTransformX(float transform);

    public static native void nativeProjectTransformY(float transform);

    public static native void nativeAutoBlinkEyes(boolean enabled);

    public static native void nativeProjectMouthForm(float value);

    public static native void nativeProjectMouthOpenY(float value);

    // Java bridge (method IDs JNI_OnLoad resolves) ----------------------------

    public static void SetContext(Context context) {
        _context = context;
    }

    public static void SetActivityInstance(Activity activity) {
        _activityInstance = activity;
    }

    public static void setCallback(Callback callback) {
        _callback = callback;
    }

    public static byte[] LoadFile(String filePath) {
        InputStream fileData = null;
        try {
            if (filePath.startsWith("/")) {
                fileData = new FileInputStream(filePath);
            } else {
                fileData = _context.getAssets().open(filePath);
            }
            int fileSize = fileData.available();
            byte[] fileBuffer = new byte[fileSize];
            fileData.read(fileBuffer, 0, fileSize);
            return fileBuffer;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (fileData != null) {
                    fileData.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void MoveTaskToBack() {
        _activityInstance.moveTaskToBack(true);
    }

    public static void OnLoadError() {
        if (_callback != null) {
            _callback.onLoadError();
        }
    }

    public static void OnLoadDone() {
        if (_callback != null) {
            _callback.onLoadDone();
        }
    }

    public static void OnLoadOneMotion(String motionGroup, int index, String motionName) {
        if (_callback != null) {
            _callback.onLoadOneMotion(motionGroup, index, motionName);
        }
    }

    public static void OnLoadOneExpression(String expressionName, int index) {
        // lab 验收通道：证明 native 真的从 model3.json 注册了这个表情
        // （lab 清单写错位置时这里一个都不会出现；demo #2 的表情资产回归锚点）。
        android.util.Log.i("Live2dTouch", "expression registered: " + expressionName + " #" + index);
        if (_callback != null) {
            _callback.onLoadOneExpression(expressionName, index);
        }
    }

    public interface Callback {
        void onLoadError();

        void onLoadDone();

        void onLoadOneMotion(String motionGroup, int index, String motionName);

        void onLoadOneExpression(String expressionName, int index);
    }
}
