package com.openapi.ks.myapp.video.player;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.openapi.common.util.log.LogUtil;
import com.openapi.ks.myapp.cast.CastFrameworkUtil;

/**
 * 播放器右上角的 Google Cast 投屏按钮（从 {@link DetailGSYVideoPlayer} 拆出）。
 * 需系统已安装 Google Play 服务，否则不显示。
 */
final class CastButtonBinder {

    private MediaRouteButton button;

    void install(ViewGroup parent, AppCompatActivity activity) {
        if (button != null || activity == null) {
            return;
        }
        if (CastFrameworkUtil.tryGetCastContext(activity) == null) {
            LogUtil.i("Google Cast unavailable (no Cast dynamite / GMS)");
            return;
        }
        button = new MediaRouteButton(activity);
        try {
            CastButtonFactory.setUpMediaRouteButton(activity, button);
        } catch (Throwable t) {
            LogUtil.i("CastButtonFactory.setUpMediaRouteButton failed: " + t.getMessage());
            button = null;
            return;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        int m12 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
                parent.getResources().getDisplayMetrics());
        int m48 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48,
                parent.getResources().getDisplayMetrics());
        lp.setMargins(0, m48, m12, 0);
        parent.addView(button, lp);
    }
}
