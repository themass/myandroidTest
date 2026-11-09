package com.openapi.ks.myapp.auxio;

import android.app.Activity;

import com.openapi.ks.auxio.musicui.AuxioMusicUiActivity;
import com.openapi.ks.auxio.musicui.MusicTrack;
import com.openapi.ks.myapp.bean.vo.SoundItemsVo;
import com.openapi.ks.myapp.constant.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * 宿主侧胶水：启动 GPL 音乐 UI（静态列表或走与 {@link com.openapi.ks.myapp.ui.fragment.SoundItemsMusicFragment} 相同接口的远程列表）。
 */
public final class AuxioMusicUiLauncher {

    private static final String VOLLEY_TAG = "auxio_music_ui";

    private AuxioMusicUiLauncher() {
    }

    /**
     * 进入 Auxio 风格界面时由模块内请求 {@link Constants#API_SOUND_ITEMS_URL}，与频道列表数据一致。
     */
    public static void openSoundChannelRemote(Activity activity, String channel, String keyword, String playlistTitle) {
        if (activity == null || channel == null || channel.isEmpty()) {
            return;
        }
        String kw = keyword != null ? keyword : "";
        AuxioMusicUiActivity.startRemoteFromSoundChannel(
                activity,
                Constants.getUrl(""),
                channel,
                kw,
                playlistTitle != null ? playlistTitle : "",
                VOLLEY_TAG
        );
    }

    public static boolean openFromSoundItems(Activity activity, List<SoundItemsVo> vos, String playlistTitle) {
        if (activity == null || vos == null || vos.isEmpty()) {
            return false;
        }
        ArrayList<MusicTrack> tracks = new ArrayList<>();
        long id = 0L;
        for (SoundItemsVo v : vos) {
            if (v == null || v.file == null || v.file.isEmpty()) {
                continue;
            }
            String title = v.name != null ? v.name : "";
            tracks.add(new MusicTrack(id++, title, v.file, null));
        }
        if (tracks.isEmpty()) {
            return false;
        }
        AuxioMusicUiActivity.start(activity, tracks, playlistTitle != null ? playlistTitle : "");
        return true;
    }
}
