package com.openapi.ks.myapp.ui.fragment;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;
import com.openapi.core.design.dialog.AppDialogHost;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.openapi.core.event.FavoriteVo;
import com.openapi.core.view.translation.TranslationOverlaySession;
import com.openapi.core.view.translation.VideoTranslationRegistry;
import com.openapi.core.view.translation.VideoTranslationRequest;
import com.openapi.core.view.view.MyFavoriteView;
import com.openapi.core.util.CollectionUtils;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.MediaUtil;
import com.openapi.core.util.PreferenceUtils;
import com.openapi.core.util.ToastUtil;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.core.net.NetUtils;
import com.openapi.ks.myapp.adapter.SoundItemsViewMusicAdapter;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.core.net.request.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.bean.vo.SoundItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.core.util.StaticDataUtil;
import com.openapi.ks.myapp.service.PlayService;
import com.openapi.ks.myapp.translation.realtime.RealtimeSubtitleBindings;
import com.openapi.ks.myapp.translation.realtime.VideoRealtimeSubtitleSession;
import com.openapi.ks.myapp.auxio.AuxioMusicUiLauncher;
import com.openapi.ks.myapp.ui.base.CommonFragmentActivity;
import com.openapi.ks.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.myapp.ui.inte.MusicStateListener;
import com.openapi.feature.channel.R;


import static com.openapi.ks.myapp.service.PlayService.CURRENT;
import static com.openapi.ks.myapp.service.PlayService.CURRENTTIME;
import static com.openapi.ks.myapp.service.PlayService.DURATION;
import static com.openapi.ks.myapp.service.PlayService.MUSIC_CURRENT;
import static com.openapi.ks.myapp.service.PlayService.MUSIC_DURATION;
import static com.openapi.ks.myapp.service.PlayService.MUSIC_PREPARED;
import static com.openapi.ks.myapp.service.PlayService.ACTION_PLAY_MODE_CHANGED;
import static com.openapi.ks.myapp.service.PlayService.MUSIC_PREPAREING;
import static com.openapi.ks.myapp.service.PlayService.UPDATE_ACTION;

/**
 * Created by openapi on 2016/8/12.
 */
public class SoundItemsMusicFragment extends BasePullLoadbleFragment<SoundItemsVo> implements SeekBar.OnSeekBarChangeListener, View.OnClickListener, MusicStateListener, MyFavoriteView.OnFavoriteItemClick {
    private static final String SOUND_TAG = "SOUND_TAG";
    @Nullable
    SeekBar progressView;
    @Nullable
    ImageView ivPre;
    @Nullable
    ImageView ivPlay;
    @Nullable
    ImageView ivNext;
    @Nullable
    ImageView ivPlayMode;
    @Nullable
    TextView tvTime;
    @Nullable
    TextView tvTitle;
    @Nullable
    TextView tvDuration;
    @Nullable
    ProgressBar pbLoading;
    @Nullable
    MyFavoriteView myFavoriteView;

    boolean ignorNet;
    private SoundItemsViewMusicAdapter adapter;
    @Nullable
    private TextView tvPlaySubtitle;
    private String channel;
    private RecommendVo vo;
    private PlayService mService;
    private boolean mBound = false;
    @Override
    protected void bindViews(View view) {
        super.bindViews(view);
        progressView = view.findViewById(R.id.progress);
        ivPre = view.findViewById(R.id.iv_pre);
        ivPlay = view.findViewById(R.id.iv_play);
        ivNext = view.findViewById(R.id.iv_next);
        ivPlayMode = view.findViewById(R.id.iv_play_mode);
        tvTime = view.findViewById(R.id.time);
        tvTitle = view.findViewById(R.id.tv_play_title);
        tvDuration = view.findViewById(R.id.duration);
        pbLoading = view.findViewById(R.id.pb_loading);
        myFavoriteView = view.findViewById(R.id.my_favoriteview);
    }

    @Override
    protected void unbindViews() {
        progressView = null;
        ivPre = null;
        ivPlay = null;
        ivNext = null;
        ivPlayMode = null;
        tvTime = null;
        tvTitle = null;
        tvDuration = null;
        pbLoading = null;
        myFavoriteView = null;
        super.unbindViews();
    }


    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to PlayerService, cast the IBindViewer and get PlayerService instance
            PlayService.LocalBinder binder = (PlayService.LocalBinder) service;
            mService = binder.getService();
            LogUtil.i("bind music mService");
            if (!CollectionUtils.isEmpty(infoListVo.voList)) {
                mService.setData(infoListVo.voList);
            }
            mBound = true;
            applyPlayModeIcon();
            syncPlayButtonFromService();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName classname) {
            mBound = false;
        }
    };
    private int current = -1;
    private int currentTime;        //当前播放进度
    private int duration;            //播放长度
    private PlayerReceiver homeReceiver;
    private NetWorkingReceiver netReceiver;
    private ViewGroup musicPageRoot;

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, SoundItemsMusicFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.sound);
        StaticDataUtil.add(Constants.SOUND_CHANNEL, vo);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.BANNER_ADS_CATEGRY, AdsContext.Categrey.CATEGREY_VPN1);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS_SHOW, true);
        intent.putExtra(CommonFragmentActivity.FABUP_SHOW, false);
        intent.putExtra(CommonFragmentActivity.TOOLBAR_SHOW, false);
        context.startActivity(intent);
    }

    private void openSoundDetail() {
        if (getActivity() == null || vo == null) {
            return;
        }
        StaticDataUtil.add(Constants.SOUND_DETAIL_VO, vo);
        startActivity(new Intent(getActivity(), com.openapi.ks.myapp.ui.sound.SoundDetailActivity.class));
    }

    @Override
    protected boolean showSearchView() {
        return true;
    }

    private void receiverReg() {
        homeReceiver = new PlayerReceiver();
        // 创建IntentFilter
        IntentFilter filter = new IntentFilter();
        // 指定BroadcastReceiver监听的Action
        filter.addAction(UPDATE_ACTION);
        filter.addAction(MUSIC_CURRENT);
        filter.addAction(MUSIC_DURATION);
        filter.addAction(MUSIC_PREPAREING);
        filter.addAction(MUSIC_PREPARED);
        filter.addAction(ACTION_PLAY_MODE_CHANGED);
        // 注册BroadcastReceiver
        getActivity().registerReceiver(homeReceiver, filter);

        IntentFilter filter1 = new IntentFilter();
        filter1.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter1.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter1.addAction("android.net.wifi.STATE_CHANGE");
        netReceiver = new NetWorkingReceiver();
        getActivity().registerReceiver(netReceiver, filter1);
    }

    public void onClick(View view) {
        if (mService == null) {
            return;
        }
        // library 模块的 R.id 不是编译期常量，不能用于 switch
        int id = view.getId();
        if (id == R.id.iv_play_mode) {
            cyclePlayMode();
        } else if (id == R.id.iv_play) {
            if (isPlaying()) {
                pause();
            } else {
                if (current == -1) {
                    play(0);
                } else {
                    resume();
                }
            }
        } else if (id == R.id.iv_next) {
            next(); // 下一曲
        } else if (id == R.id.iv_pre) {
            pre(); // 上一曲
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * 继续播放
     *
     * @return 当前播放的位置 默认为0
     */
    public int resume() {
        if (mService == null) {
            return -1;
        }
        if (mService.isPlaying())
            return -1;
        mService.resume();
        ivPlay
                .setImageResource(R.drawable.ic_music_pause_filled);
        return mService.getPosition();
    }

    /**
     * 暂停播放
     *
     * @return 当前播放的位置
     */
    public int pause() {
        if (!mService.isPlaying())
            return -1;
        mService.pause(); // 暂停
        ivPlay
                .setImageResource(R.drawable.ic_music_play_filled);
        return mService.getPosition();
    }

    public boolean isPlaying() {
        return mService != null && mService.isPlaying();
    }

    @Override
    public boolean showMusicListSpectrum() {
        if (mService != null && mService.isPlaying()) {
            return true;
        }
        return pbLoading != null && pbLoading.getVisibility() == View.VISIBLE;
    }

    /**
     * 下一曲
     *
     * @return 当前播放的位置
     */
    public void next() {
        if (mService == null || adapter.getItemCount() <= 0) {
            return;
        }
        play(mService.computeNextIndex(current, adapter.getItemCount()));
    }

    /**
     * 上一曲
     *
     * @return 当前播放的位置
     */
    public void pre() {
        if (mService == null || adapter.getItemCount() <= 0) {
            return;
        }
        play(mService.computePreviousIndex(current, adapter.getItemCount()));
    }

    private void cyclePlayMode() {
        if (mService == null) {
            return;
        }
        int m = mService.getPlayMode();
        int next = (m == 3) ? 2 : (m == 2) ? 1 : (m == 1) ? 4 : 3;
        mService.setPlayMode(next);
        ToastUtil.showShort(getString(R.string.sound_play_mode_toast, playModeLabel(next)));
    }

    private String playModeLabel(int mode) {
        switch (mode) {
            case 1:
                return getString(R.string.sound_play_mode_one);
            case 2:
                return getString(R.string.sound_play_mode_all);
            case 3:
                return getString(R.string.sound_play_mode_list);
            case 4:
                return getString(R.string.sound_play_mode_shuffle);
            default:
                return getString(R.string.sound_play_mode_list);
        }
    }

    private void applyPlayModeIcon() {
        if (ivPlayMode == null || getActivity() == null) {
            return;
        }
        if (!mBound || mService == null) {
            ivPlayMode.setImageResource(R.drawable.ic_play_mode_order);
            ivPlayMode.setContentDescription(getString(R.string.sound_play_mode_cycle));
            return;
        }
        int mode = mService.getPlayMode();
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
        ivPlayMode.setImageResource(icon);
        ivPlayMode.setContentDescription(getString(R.string.sound_play_mode_cycle) + "：" + playModeLabel(mode));
    }

    public void play(int position) {
        LogUtil.i("play(int position)  --" + position);
        if (adapter.getItemCount() <= 0) {
            ToastUtil.showShort(
                    "该频道没有sex love");
            return;
        }
        if (position < 0) {
            position = 0;
        }
        if (position == 0) {
            adapter.setSelected(0);
        }
        if (position >= adapter.getItemCount())
            position = adapter.getItemCount() - 1;
        // 开始播放应显示「暂停」；原先误设为 play，导致与 MediaPlayer 实际状态不一致
        ivPlay.setImageResource(R.drawable.ic_music_pause_filled);
        mService.playPosi(position);
    }

    /** 与 {@link PlayService#isPlaying()} 对齐底栏播放键图标（绑定服务、回到前台时调用） */
    private void syncPlayButtonFromService() {
        if (ivPlay == null || mService == null) {
            return;
        }
        ivPlay.setImageResource(mService.isPlaying()
                ? R.drawable.ic_music_pause_filled
                : R.drawable.ic_music_play_filled);
    }

    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        boolean auxio = PreferenceUtils.getPrefBoolean(parent.getContext(), Constants.MUSIC_UI_AUXIO_STYLE, false);
        inflater.inflate(auxio ? R.layout.layout_sound_list_music_auxio : R.layout.layout_sound_list_music,
                parent, true);
    }

    protected BaseRecyclerViewAdapter getAdapter() {
        boolean auxio = PreferenceUtils.getPrefBoolean(getActivity(), Constants.MUSIC_UI_AUXIO_STYLE, false);
        adapter = new SoundItemsViewMusicAdapter(
                getActivity(), pullView.getRecyclerView(), infoListVo.voList, this, auxio);
        adapter.setPlayServise(this);
        return adapter;
    }

    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        musicPageRoot = (ViewGroup) view;
        vo = StaticDataUtil.get(Constants.SOUND_CHANNEL, RecommendVo.class);
        if (vo == null) {
            getActivity().finish();
        }
        StaticDataUtil.del(Constants.SOUND_CHANNEL);
        channel = vo.param;
        Intent intent = new Intent(getActivity(), PlayService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        LogUtil.i("start bind music service");
        myFavoriteView.initFavoriteBackGroud(vo.param);
        myFavoriteView.setListener(this);
        myFavoriteView.showVideoLocal();
        myFavoriteView.setTranslationSession(new TranslationOverlaySession() {
            @Override
            public ViewGroup getOverlayParent() {
                return musicPageRoot;
            }

            @Override
            public void fillRequest(VideoTranslationRequest.Builder builder) {
                int idx = current >= 0 ? current : adapter.getSelected();
                if (idx < 0) {
                    idx = 0;
                }
                if (infoListVo.voList == null || idx >= infoListVo.voList.size()) {
                    throw new IllegalStateException("no track");
                }
                SoundItemsVo v = infoListVo.voList.get(idx);
                builder.setVideoUrl(v.file)
                        .putExtra(VideoTranslationRequest.EXTRA_MEDIA_KIND,
                                VideoTranslationRequest.MEDIA_KIND_AUDIO);
            }

            @Override
            public VideoTranslationRegistry registry() {
                return VideoTranslationRegistry.getInstance();
            }
        });
        VideoRealtimeSubtitleSession realtimeSession = RealtimeSubtitleBindings.forMusic(
                requireActivity(),
                musicPageRoot,
                () -> {
                    int idx = current >= 0 ? current : adapter.getSelected();
                    if (idx < 0) {
                        idx = 0;
                    }
                    if (infoListVo.voList == null || idx >= infoListVo.voList.size()) {
                        return "";
                    }
                    SoundItemsVo track = infoListVo.voList.get(idx);
                    return track != null && track.file != null ? track.file : "";
                },
                new RealtimeSubtitleBindings.MusicPlaybackProbe() {
                    @Override
                    public boolean isPlaying() {
                        return SoundItemsMusicFragment.this.isPlaying();
                    }

                    @Override
                    public long positionMs() {
                        if (mService != null) {
                            return mService.getPosition();
                        }
                        return currentTime;
                    }
                });
        RealtimeSubtitleBindings.attachMusic(myFavoriteView, realtimeSession,
                new RealtimeSubtitleBindings.MusicPlaybackProbe() {
                    @Override
                    public boolean isPlaying() {
                        return SoundItemsMusicFragment.this.isPlaying();
                    }

                    @Override
                    public long positionMs() {
                        if (mService != null) {
                            return mService.getPosition();
                        }
                        return currentTime;
                    }
                });
        tvTitle.setOnClickListener(v -> openSoundDetail());
        tvPlaySubtitle = view.findViewById(R.id.tv_play_subtitle);
        View gplEntry = view.findViewById(R.id.tv_open_auxio_gpl_module);
        if (gplEntry != null) {
            gplEntry.setOnClickListener(v -> {
                String title = vo != null && vo.title != null ? vo.title : "";
                AuxioMusicUiLauncher.openSoundChannelRemote(
                        getActivity(),
                        channel,
                        keyword != null ? keyword : "",
                        title
                );
            });
        }
        if (ivPlayMode != null) {
            ivPlayMode.setOnClickListener(this);
        }
        ivPre.setOnClickListener(this);
        ivPlay.setOnClickListener(this);
        ivNext.setOnClickListener(this);
        progressView.setOnSeekBarChangeListener(this);
//        ViewGroup group= (ViewGroup)LayoutInflater.from(getActivity()).inflate(R.layout.layout_youmi_native_video,null);
//        AdsContext.nativeVideoAds(getActivity(),group);
//        adapter.setFooterView(group);
        receiverReg();
    }

    @Override
    protected void onDataLoaded(InfoListVo<SoundItemsVo> data) {
        super.onDataLoaded(data);
        if (mService != null) {
            mService.setData(infoListVo.voList);
            syncPlayButtonFromService();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        syncPlayButtonFromService();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        if (myFavoriteView != null) {
            myFavoriteView.releaseTranslationOverlay();
        }
        indexService.cancelRequest(SOUND_TAG);
        getActivity().unregisterReceiver(homeReceiver);
        getActivity().unregisterReceiver(netReceiver);
//        getActivity().unbindService(mConnection);
        super.onDestroyView();
    }

    @Override
    protected InfoListVo<SoundItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_SOUND_ITEMS_URL, infoListVo.pageNum, channel, keyword), SoundItemsVo.class, SOUND_TAG);
    }

    @Override
    public void onItemClick(View view, SoundItemsVo data, int postion) {
        LogUtil.i(data.file);
        play(postion);
        super.onItemClick(view, data, postion);
    }

    public void onProgressChanged(SeekBar var1, int var2, boolean var3) {
        LogUtil.i("前：" + var1.getProgress() + "; 后：" + var2);
    }

    public void onStartTrackingTouch(SeekBar var1) {
        LogUtil.i("开始拖动");
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        LogUtil.i("停止拖动");
        mService.play(seekBar.getProgress());
        ivPlay.setImageResource(R.drawable.ic_music_pause_filled);
    }

    public boolean checkCanPlay() {
        boolean soundSwitch = PreferenceUtils.getPrefBoolean(getActivity(), Constants.SOUND_SWITCH, false);
        return soundSwitch || NetUtils.isWifi(getActivity()) || ignorNet;
    }

    public void showUpdateDialog(final Activity context) {
        AppDialogHost.showMessage(context, null,
                context.getString(R.string.sound_play_not_wifi), null,
                context.getString(R.string.del_cancel), null,
                context.getString(R.string.del_ok), () -> {
                    ignorNet = true;
                    resume();
                },
                true);
    }

    @Override
    public FavoriteVo getFavoriteDataUrl() {
        return vo.tofavorite(Constants.FavoriteType.SOUND);
    }

    @Override
    public String getBrowserDataUrl() {
        return infoListVo.voList.get(adapter.getSelected() < 0 ? 0 : adapter.getSelected()).file;
    }

    @Override
    public boolean setSpeed(float speed) {
        if (mService == null) {
            return false;
        }
        mService.setPlaybackSpeed(speed);
        return true;
    }

    /**
     * 用来接收从service传回来的广播的内部类
     */
    public class PlayerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                if (action.equals(MUSIC_PREPAREING)) {
                    pbLoading.setVisibility(View.VISIBLE);
                    tvTime.setVisibility(View.GONE);
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    return;
                }
                pbLoading.setVisibility(View.GONE);
                tvTime.setVisibility(View.VISIBLE);
                if (action.equals(MUSIC_CURRENT)) {
                    currentTime = intent.getIntExtra(CURRENTTIME, -1);
                    tvTime.setText(MediaUtil.formatTime(currentTime));
                    progressView.setProgress(currentTime);
                } else if (action.equals(MUSIC_DURATION)) {
                    int duration = intent.getIntExtra(DURATION, -1);
                    progressView.setMax(duration);
                    tvDuration.setText(MediaUtil.formatTime(duration));
                } else if (action.equals(UPDATE_ACTION)) {
                    //获取Intent中的current消息，current代表当前正在播放的歌曲
                    current = intent.getIntExtra(CURRENT, -1);
                    tvTitle.setText(infoListVo.voList.get(current).name);
                    if (tvPlaySubtitle != null && current >= 0 && current < infoListVo.voList.size()) {
                        tvPlaySubtitle.setText(infoListVo.voList.get(current).fileDate);
                    }
                    adapter.setSelected(current);
                    adapter.notifyDataSetChanged();
                    LogUtil.i("当前播放更新：" + current);
                    syncPlayButtonFromService();
                } else if (action.equals(MUSIC_PREPARED)) {
                    //获取Intent中的current消息，current代表当前正在播放的歌曲
                    if (!checkCanPlay() && isPlaying()) {
                        pause();
                        showUpdateDialog(getActivity());
                    }
                    syncPlayButtonFromService();
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                } else if (ACTION_PLAY_MODE_CHANGED.equals(action)) {
                    applyPlayModeIcon();
                }
            } catch (Exception e) {
                LogUtil.e(e);
            }
        }
    }

    public class NetWorkingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (mBound)
                    if (!checkCanPlay() && isPlaying()) {
                        pause();
                        showUpdateDialog(getActivity());
                    }
            } catch (Exception e) {
                LogUtil.e(e);
            }
        }
    }
}
