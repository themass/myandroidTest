package com.timeline.vpn.ui.fragment;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.sspacee.common.ui.view.FavoriteImageView;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.MediaUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.net.NetUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.SoundItemsViewAdapter;
import com.timeline.vpn.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.vpn.bean.vo.InfoListVo;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.bean.vo.SoundItemsVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.StaticDataUtil;
import com.timeline.vpn.service.PlayService;
import com.timeline.vpn.ui.base.CommonFragmentActivity;

import butterknife.BindView;
import butterknife.OnClick;

import static com.timeline.vpn.service.PlayService.CURRENT;
import static com.timeline.vpn.service.PlayService.CURRENTTIME;
import static com.timeline.vpn.service.PlayService.DURATION;
import static com.timeline.vpn.service.PlayService.MUSIC_CURRENT;
import static com.timeline.vpn.service.PlayService.MUSIC_DURATION;
import static com.timeline.vpn.service.PlayService.MUSIC_PREPARED;
import static com.timeline.vpn.service.PlayService.MUSIC_PREPAREING;
import static com.timeline.vpn.service.PlayService.UPDATE_ACTION;

/**
 * Created by themass on 2016/8/12.
 */
public class SoundItemsFragment extends BasePullLoadbleFragment<SoundItemsVo> implements SeekBar.OnSeekBarChangeListener, View.OnClickListener {
    private static final String SOUND_TAG = "SOUND_TAG";
    @Nullable
    @BindView(R.id.progress)
    SeekBar progressView;
    @Nullable
    @BindView(R.id.iv_pre)
    ImageView ivPre;
    @Nullable
    @BindView(R.id.iv_play)
    ImageView ivPlay;
    @Nullable
    @BindView(R.id.iv_next)
    ImageView ivNext;
    @Nullable
    @BindView(R.id.time)
    TextView tvTime;
    @Nullable
    @BindView(R.id.tv_play_title)
    TextView tvTitle;
    @Nullable
    @BindView(R.id.duration)
    TextView tvDuration;
    @Nullable
    @BindView(R.id.pb_loading)
    ProgressBar pbLoading;
    @BindView(R.id.iv_favorite)
    FavoriteImageView ivFavorite;
    boolean ignorNet;
    private SoundItemsViewAdapter adapter;
    private String channel;
    private RecommendVo vo;
    private PlayService mService;
    private boolean mBound = false;
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to PlayerService, cast the IBindViewer and get PlayerService instance
            PlayService.LocalBinder binder = (PlayService.LocalBinder) service;
            mService = binder.getService();
            if (!CollectionUtils.isEmpty(infoListVo.voList)) {
                mService.setData(infoListVo.voList);
            }
            mBound = true;
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

    public static void startFragment(Context context, RecommendVo vo) {
        Intent intent = new Intent(context, CommonFragmentActivity.class);
        intent.putExtra(CommonFragmentActivity.FRAGMENT, SoundItemsFragment.class);
        intent.putExtra(CommonFragmentActivity.TITLE, R.string.sound);
        StaticDataUtil.add(Constants.SOUND_CHANNEL, vo);
        intent.putExtra(CommonFragmentActivity.ADS, true);
        intent.putExtra(CommonFragmentActivity.ADSSCROLL, false);
        intent.putExtra(CommonFragmentActivity.SLIDINGCLOSE, true);
        intent.putExtra(CommonFragmentActivity.INTERSTITIAL_ADS, true);
        context.startActivity(intent);
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
        // 注册BroadcastReceiver
        getActivity().registerReceiver(homeReceiver, filter);

        IntentFilter filter1 = new IntentFilter();
        filter1.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter1.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter1.addAction("android.net.wifi.STATE_CHANGE");
        netReceiver = new NetWorkingReceiver();
        getActivity().registerReceiver(netReceiver, filter1);
    }

    @OnClick(R.id.iv_favorite)
    public void favoriteClick(View view) {
        ivFavorite.clickFavorite(vo.tofavorite(Constants.FavoriteType.SOUND));
    }

    public void onClick(View view) {
        if(mService==null){
            return ;
        }
        switch (view.getId()) {
            case R.id.iv_play:
                if (isPlaying()) {
                    pause();
                } else {
                    if (current == -1) {
                        play(0);
                    } else {
                        resume();
                    }
                }
                break;
            case R.id.iv_next:
                next(); // 下一曲
                break;
            case R.id.iv_pre:
                pre(); // 上一曲
                break;
        }
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
                .setImageResource(android.R.drawable.ic_media_pause);
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
                .setImageResource(android.R.drawable.ic_media_play);
        return mService.getPosition();
    }

    public boolean isPlaying() {
        return mService.isPlaying();
    }

    /**
     * 下一曲
     *
     * @return 当前播放的位置
     */
    public void next() {
        if (current >= adapter.getItemCount()) {
            play(current);
        }
        play(current + 1);
    }

    /**
     * 上一曲
     *
     * @return 当前播放的位置
     */
    public void pre() {
        if (current <= 0) {
            play(adapter.getItemCount() - 1);
        }
        play(current - 1);
    }

    public void play(int position) {
        LogUtil.i("play(int position)  --" + position);
        if (adapter.getItemCount() <= 0) {
            Toast.makeText(getActivity(),
                    "该频道没有sex love", Toast.LENGTH_LONG).show();
            return;
        }
        if (position < 0)
            position = 0;
        if (position >= adapter.getItemCount())
            position = adapter.getItemCount() - 1;
        ivPlay
                .setImageResource(android.R.drawable.ic_media_play);
        mService.playPosi(position);
    }

    @Override
    public void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_sound_list, parent, true);
    }
    protected BaseRecyclerViewAdapter getAdapter(){
        adapter = new SoundItemsViewAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        return adapter;
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        vo = StaticDataUtil.get(Constants.SOUND_CHANNEL, RecommendVo.class);
        StaticDataUtil.del(Constants.SOUND_CHANNEL);
        channel = vo.param;
        Intent intent = new Intent(getActivity(), PlayService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        ivFavorite.initSrc(vo.param);
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
        }
    }

    @Override
    public void onDestroyView() {
        indexService.cancelRequest(SOUND_TAG);
        getActivity().unregisterReceiver(homeReceiver);
        getActivity().unregisterReceiver(netReceiver);
        getActivity().unbindService(mConnection);
        super.onDestroyView();
    }

    @Override
    protected InfoListVo<SoundItemsVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getPageWithParam_URL(Constants.API_SOUND_ITEMS_URL, infoListVo.pageNum, channel), SoundItemsVo.class, SOUND_TAG);
    }

    @Override
    public void onItemClick(View view, SoundItemsVo data, int postion) {
        LogUtil.i(data.file);
        play(postion);
        pullView.notifyDataSetChanged();
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
        ivPlay.setImageResource(android.R.drawable.ic_media_pause);
    }

    public boolean checkCanPlay() {
        boolean soundSwitch = PreferenceUtils.getPrefBoolean(getActivity(), Constants.SOUND_SWITCH, false);
        return soundSwitch || NetUtils.isWifi(getActivity()) || ignorNet;
    }

    public void showUpdateDialog(final Activity context) {
        AlertDialog.Builder confirmDialog = new AlertDialog.Builder(context);
        confirmDialog.setMessage(R.string.sound_play_not_wifi);
        confirmDialog.setPositiveButton(R.string.del_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        confirmDialog.setNegativeButton(R.string.del_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ignorNet = true;
                resume();
                dialog.dismiss();
            }
        });
        confirmDialog.show();
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
                    LogUtil.i("当前播放更新：" + current);
                } else if (action.equals(MUSIC_PREPARED)) {
                    //获取Intent中的current消息，current代表当前正在播放的歌曲
                    ivPlay.setImageResource(android.R.drawable.ic_media_pause);
                    if (!checkCanPlay() && isPlaying()) {
                        pause();
                        showUpdateDialog(getActivity());
                    }
                }
            } catch (Exception e) {
                LogUtil.e(e);
            }
        }
    }

    public class NetWorkingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!checkCanPlay() && isPlaying()) {
                pause();
                showUpdateDialog(getActivity());
            }
        }
    }
}
