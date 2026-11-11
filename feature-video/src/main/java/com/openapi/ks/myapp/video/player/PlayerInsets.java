package com.openapi.ks.myapp.video.player;

import android.util.TypedValue;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.openapi.feature.video.R;

/**
 * 播放器顶部/底部栏的刘海和导航栏避让（从 {@link DetailGSYVideoPlayer} 拆出）。
 */
final class PlayerInsets {

    private PlayerInsets() {
    }

    static void apply(View player) {
        View top = player.findViewById(R.id.layout_top);
        if (top != null) {
            ViewCompat.setOnApplyWindowInsetsListener(top, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), sys.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }
        View bottom = player.findViewById(R.id.layout_bottom);
        if (bottom != null) {
            final int baseNavPad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,
                    player.getResources().getDisplayMetrics());
            ViewCompat.setOnApplyWindowInsetsListener(bottom, (v, insets) -> {
                Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                        baseNavPad + nav.bottom);
                return insets;
            });
        }
        ViewCompat.requestApplyInsets(player);
    }
}
