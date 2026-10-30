package com.openapi.common.ui.base;

import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15+ (targetSdk 35+) edge-to-edge support.
 */
public final class EdgeToEdgeHelper {

    private EdgeToEdgeHelper() {
    }

    public static void enable(ComponentActivity activity) {
        EdgeToEdge.enable(activity);
    }

    /** Full-screen pages (splash, etc.): pad content for system bars. */
    public static void applyRootPadding(View root) {
        if (root == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** Toolbar + body layout: status bar on toolbar, nav bar on content. */
    public static void applyToolbarLayout(View toolbar, View body) {
        if (toolbar != null) {
            final int pl = toolbar.getPaddingLeft();
            final int pr = toolbar.getPaddingRight();
            final int pb = toolbar.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(pl, bars.top, pr, pb);
                return insets;
            });
            ViewCompat.requestApplyInsets(toolbar);
        }
        if (body != null) {
            final int pl = body.getPaddingLeft();
            final int pt = body.getPaddingTop();
            final int pr = body.getPaddingRight();
            ViewCompat.setOnApplyWindowInsetsListener(body, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(pl, pt, pr, bars.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(body);
        }
    }
}
