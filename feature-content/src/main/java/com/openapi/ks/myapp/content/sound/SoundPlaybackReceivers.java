package com.openapi.ks.myapp.content.sound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.openapi.common.util.log.LogUtil;

import static com.openapi.ks.myapp.content.sound.PlayService.ACTION_PLAY_MODE_CHANGED;
import static com.openapi.ks.myapp.content.sound.PlayService.CURRENT;
import static com.openapi.ks.myapp.content.sound.PlayService.CURRENTTIME;
import static com.openapi.ks.myapp.content.sound.PlayService.DURATION;
import static com.openapi.ks.myapp.content.sound.PlayService.MUSIC_CURRENT;
import static com.openapi.ks.myapp.content.sound.PlayService.MUSIC_DURATION;
import static com.openapi.ks.myapp.content.sound.PlayService.MUSIC_PREPARED;
import static com.openapi.ks.myapp.content.sound.PlayService.MUSIC_PREPAREING;
import static com.openapi.ks.myapp.content.sound.PlayService.UPDATE_ACTION;

/**
 * 声音列表页的两个广播接收器：只负责把 Intent 解析成事件，界面更新由 {@link Host} 的实现（{@link SoundItemsMusicFragment}）完成。
 */
final class SoundPlaybackReceivers {

    private SoundPlaybackReceivers() {
    }

    interface Host {
        void onPreparing();

        void onPositionChanged(int positionMs);

        void onDurationChanged(int durationMs);

        void onTrackChanged(int index);

        void onPrepared();

        void onPlayModeChanged();

        void onNetworkChanged();
    }

    /** 接收 {@link PlayService} 发出的播放状态广播。 */
    static BroadcastReceiver player(Host host) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String action = intent.getAction();
                    if (action.equals(MUSIC_PREPAREING)) {
                        host.onPreparing();
                    } else if (action.equals(MUSIC_CURRENT)) {
                        host.onPositionChanged(intent.getIntExtra(CURRENTTIME, -1));
                    } else if (action.equals(MUSIC_DURATION)) {
                        host.onDurationChanged(intent.getIntExtra(DURATION, -1));
                    } else if (action.equals(UPDATE_ACTION)) {
                        host.onTrackChanged(intent.getIntExtra(CURRENT, -1));
                    } else if (action.equals(MUSIC_PREPARED)) {
                        host.onPrepared();
                    } else if (ACTION_PLAY_MODE_CHANGED.equals(action)) {
                        host.onPlayModeChanged();
                    }
                } catch (Exception e) {
                    LogUtil.e(e);
                }
            }
        };
    }

    /** 接收网络状态变化，移动网络下未授权时暂停播放。 */
    static BroadcastReceiver network(Host host) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    host.onNetworkChanged();
                } catch (Exception e) {
                    LogUtil.e(e);
                }
            }
        };
    }
}
