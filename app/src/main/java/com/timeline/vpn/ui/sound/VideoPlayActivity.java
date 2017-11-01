//package com.timeline.vpn.ui.sound;
//
//
//import android.net.Uri;
//import android.os.Bundle;
//import android.support.annotation.Nullable;
//import android.view.View;
//import android.widget.ProgressBar;
//import android.widget.TextView;
//
//import com.sspacee.common.util.LogUtil;
//import com.sspacee.yewu.ads.base.AdsContext;
//import com.sspacee.yewu.ads.base.AdsManager;
//import com.timeline.vpn.R;
//import com.timeline.vpn.bean.vo.RecommendVo;
//import com.timeline.vpn.constant.Constants;
//import com.timeline.vpn.data.UserLoginUtil;
//import com.timeline.vpn.ui.base.app.BaseFragmentActivity;
//
//import butterknife.BindView;
//import io.vov.vitamio.LibsChecker;
//import io.vov.vitamio.MediaPlayer;
//import io.vov.vitamio.MediaPlayer.OnBufferingUpdateListener;
//import io.vov.vitamio.MediaPlayer.OnInfoListener;
//import io.vov.vitamio.widget.MediaController;
//import io.vov.vitamio.widget.VideoView;
//
///**
// * Created by themass on 2015/9/1.
// */
//public class VideoPlayActivity extends BaseFragmentActivity implements OnInfoListener, OnBufferingUpdateListener{
//    private RecommendVo vo;
//    private Uri uri;
//    @Nullable
//    @BindView(R.id.buffer)
//     VideoView mVideoView;
//    @Nullable
//    @BindView(R.id.probar)
//     ProgressBar pb;
//    @Nullable
//    @BindView(R.id.download_rate)
//    TextView downloadRateView;
//    @Nullable
//    @BindView(R.id.load_rate)
//    TextView loadRateView;
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.common_video);
//        setFabUpVisibility(View.GONE);
//        vo = (RecommendVo)getIntent().getExtras().getSerializable(Constants.CONFIG_PARAM);
//        LogUtil.i(vo.toString());
//        if (!LibsChecker.checkVitamioLibs(this))
//            return;
//        setToolbarTitle(vo.title, true);
//        initPlay();
//    }
//    public void initPlay(){
//        uri = Uri.parse(vo.actionUrl);
//        mVideoView.setVideoURI(uri);
//        mVideoView.setMediaController(new MediaController(this));
//        mVideoView.requestFocus();
//        mVideoView.setOnInfoListener(this);
//        mVideoView.setOnBufferingUpdateListener(this);
//        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//            @Override
//            public void onPrepared(MediaPlayer mediaPlayer) {
//                // optional need Vitamio 4.0
//                mediaPlayer.setPlaybackSpeed(1.0f);
//            }
//        });
//    }
//    @Override
//    public boolean onInfo(MediaPlayer mp, int what, int extra) {
//        LogUtil.i(what+"---"+extra);
//        switch (what) {
//            case MediaPlayer.MEDIA_INFO_BUFFERING_START:
//                if (mVideoView.isPlaying()) {
//                    mVideoView.pause();
//                    pb.setVisibility(View.VISIBLE);
//                    downloadRateView.setText("111");
//                    loadRateView.setText("111");
//                    downloadRateView.setVisibility(View.VISIBLE);
//                    loadRateView.setVisibility(View.VISIBLE);
//
//                }
//                break;
//            case MediaPlayer.MEDIA_INFO_BUFFERING_END:
//                mVideoView.start();
//                pb.setVisibility(View.GONE);
//                downloadRateView.setVisibility(View.GONE);
//                loadRateView.setVisibility(View.GONE);
//                break;
//            case MediaPlayer.MEDIA_INFO_DOWNLOAD_RATE_CHANGED:
//                downloadRateView.setText("" + extra + "kb/s" + "  ");
//                break;
//        }
//        return true;
//    }
//    @Override
//    public void onBufferingUpdate(MediaPlayer mp, int percent) {
//        loadRateView.setText(percent + "%");
//    }
//    @Override
//    public boolean needShow() {
//        return true;
//    }
//
//    protected boolean enableSliding() {
//        return true;
//    }
//    @Override
//    public void setupView() {
//        super.setupView();
//        if(!UserLoginUtil.isVIP2()){
//            AdsManager.getInstans().showInterstitialAds(this, AdsContext.Categrey.CATEGREY_1,false);
//        }
//    }
//}
