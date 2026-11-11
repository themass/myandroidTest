package com.openapi.ks.myapp.content.sound;

import android.content.Context;
import android.widget.ImageView;

import com.openapi.common.util.ToastUtil;
import com.openapi.feature.content.R;
import com.openapi.ks.myapp.content.sound.PlayService;

/**
 * 音乐播放模式：循环切换、文字说明和底栏图标（从 {@link SoundItemsMusicFragment} 拆出，行为不变）。
 * 模式值：1 单曲循环，2 列表循环，3 顺序，4 随机。
 */
final class PlayModeController {

    private PlayModeController() {
    }

    static void cycle(Context context, PlayService service) {
        int m = service.getPlayMode();
        int next = (m == 3) ? 2 : (m == 2) ? 1 : (m == 1) ? 4 : 3;
        service.setPlayMode(next);
        ToastUtil.showShort(context.getString(R.string.sound_play_mode_toast, label(context, next)));
    }

    static String label(Context context, int mode) {
        switch (mode) {
            case 1:
                return context.getString(R.string.sound_play_mode_one);
            case 2:
                return context.getString(R.string.sound_play_mode_all);
            case 3:
                return context.getString(R.string.sound_play_mode_list);
            case 4:
                return context.getString(R.string.sound_play_mode_shuffle);
            default:
                return context.getString(R.string.sound_play_mode_list);
        }
    }

    /** service 为 null（未绑定）时显示默认图标。 */
    static void applyIcon(Context context, ImageView view, PlayService service) {
        if (service == null) {
            view.setImageResource(R.drawable.ic_play_mode_order);
            view.setContentDescription(context.getString(R.string.sound_play_mode_cycle));
            return;
        }
        int mode = service.getPlayMode();
        int icon;
        switch (mode) {
            case 1:
                icon = R.drawable.ic_play_mode_repeat_one;
                break;
            case 2:
                icon = R.drawable.ic_play_mode_repeat_all;
                break;
            case 4:
                icon = R.drawable.ic_play_mode_shuffle;
                break;
            case 3:
            default:
                icon = R.drawable.ic_play_mode_order;
                break;
        }
        view.setImageResource(icon);
        view.setContentDescription(context.getString(R.string.sound_play_mode_cycle) + "：" + label(context, mode));
    }
}
