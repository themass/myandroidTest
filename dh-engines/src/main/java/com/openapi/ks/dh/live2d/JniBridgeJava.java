/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

package com.openapi.ks.dh.live2d;

/**
 * Vendored Live2D bridge (our package). The JNI binding itself lives in
 * {@code com.chatwaifu.live2d.JniBridgeJava} — the exact FQN the stock
 * libchatwaifu-live2d.so exports and FindClass-es inside JNI_OnLoad (same ruling as
 * ai.guiji.duix.DuixNcnn for Duix: vendored Java keeps our packages, the stock .so keeps
 * its symbols).
 *
 * Do NOT add a {@code System.loadLibrary} static block: the library is dlopen'd by
 * absolute path from the AssetStore cache via {@code com.openapi.ks.dh.Live2dNativeLoader},
 * and {@code Live2dRuntime} gates availability on that load. Calling a native here before
 * the loader ran dies with UnsatisfiedLinkError.
 *
 * Live2dNativeBridgeTest pins this delegation against the .so's export table.
 */

import android.app.Activity;
import android.content.Context;

public class JniBridgeJava {

    private JniBridgeJava() {
    }

    // Native (delegates to the upstream-FQN mirror) --------------------------

    public static void nativeOnStart() { com.chatwaifu.live2d.JniBridgeJava.nativeOnStart(); }

    public static void nativeOnPause() { com.chatwaifu.live2d.JniBridgeJava.nativeOnPause(); }

    public static void nativeOnStop() { com.chatwaifu.live2d.JniBridgeJava.nativeOnStop(); }

    public static void nativeOnDestroy() { com.chatwaifu.live2d.JniBridgeJava.nativeOnDestroy(); }

    public static void nativeOnSurfaceCreated() { com.chatwaifu.live2d.JniBridgeJava.nativeOnSurfaceCreated(); }

    public static void nativeOnSurfaceChanged(int width, int height) { com.chatwaifu.live2d.JniBridgeJava.nativeOnSurfaceChanged(width, height); }

    public static void nativeOnDrawFrame() { com.chatwaifu.live2d.JniBridgeJava.nativeOnDrawFrame(); }

    public static void nativeOnTouchesBegan(float pointX, float pointY) { com.chatwaifu.live2d.JniBridgeJava.nativeOnTouchesBegan(pointX, pointY); }

    public static void nativeOnTouchesEnded(float pointX, float pointY) { com.chatwaifu.live2d.JniBridgeJava.nativeOnTouchesEnded(pointX, pointY); }

    public static void nativeOnTouchesMoved(float pointX, float pointY) { com.chatwaifu.live2d.JniBridgeJava.nativeOnTouchesMoved(pointX, pointY); }

    public static void nativeProjectChangeTo(String modelPath, String modelJsonFileName) { com.chatwaifu.live2d.JniBridgeJava.nativeProjectChangeTo(modelPath, modelJsonFileName); }

    public static void nativeApplyExpression(String expressionName) { com.chatwaifu.live2d.JniBridgeJava.nativeApplyExpression(expressionName); }

    public static void needRenderBack(boolean back) { com.chatwaifu.live2d.JniBridgeJava.needRenderBack(back); }

    public static void nativeProjectScale(float scale) { com.chatwaifu.live2d.JniBridgeJava.nativeProjectScale(scale); }

    public static void nativeProjectTransformX(float transform) { com.chatwaifu.live2d.JniBridgeJava.nativeProjectTransformX(transform); }

    public static void nativeProjectTransformY(float transform) { com.chatwaifu.live2d.JniBridgeJava.nativeProjectTransformY(transform); }

    public static void nativeAutoBlinkEyes(boolean enabled) { com.chatwaifu.live2d.JniBridgeJava.nativeAutoBlinkEyes(enabled); }

    public static void nativeProjectMouthForm(float value) { com.chatwaifu.live2d.JniBridgeJava.nativeProjectMouthForm(value); }

    public static void nativeProjectMouthOpenY(float value) { com.chatwaifu.live2d.JniBridgeJava.nativeProjectMouthOpenY(value); }

    public static void nativeSetBackgroundColor(float r, float g, float b) { com.chatwaifu.live2d.JniBridgeJava.nativeSetBackgroundColor(r, g, b); }

    // Java (forwarded to the mirror — JNI_OnLoad caches IDs on the mirror) ---

    public static void SetContext(Context context) {
        com.chatwaifu.live2d.JniBridgeJava.SetContext(context);
    }

    public static void SetActivityInstance(Activity activity) {
        com.chatwaifu.live2d.JniBridgeJava.SetActivityInstance(activity);
    }

    public static void setLive2DLoadInterface(Live2DLoadInterface loadInterface) {
        if (loadInterface == null) {
            com.chatwaifu.live2d.JniBridgeJava.setCallback(null);
            return;
        }
        com.chatwaifu.live2d.JniBridgeJava.setCallback(new com.chatwaifu.live2d.JniBridgeJava.Callback() {
            @Override
            public void onLoadError() { loadInterface.onLoadError(); }

            @Override
            public void onLoadDone() { loadInterface.onLoadDone(); }

            @Override
            public void onLoadOneMotion(String motionGroup, int index, String motionName) { loadInterface.onLoadOneMotion(motionGroup, index, motionName); }

            @Override
            public void onLoadOneExpression(String expressionName, int index) { loadInterface.onLoadOneExpression(expressionName, index); }
        });
    }

    public interface Live2DLoadInterface {
        void onLoadError();

        void onLoadDone();

        void onLoadOneMotion(String motionGroup, int index, String motionName);

        void onLoadOneExpression(String expressionName, int index);
    }
}
