package com.qq.yewu.ads.base;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.qq.e.ads.nativ.ADSize;
import com.qq.e.ads.nativ.NativeExpressAD;
import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.e.ads.nativ.NativeExpressMediaListener;
import com.qq.e.comm.constants.AdPatternType;
import com.qq.e.comm.pi.AdData;
import com.qq.e.comm.util.AdError;
import com.qq.ks.free1.R;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.task.ScoreTask;

import java.util.HashMap;
import java.util.List;

public class GdtNativeManager implements NativeExpressAD.NativeExpressADListener {
    private String TAG = "mynative";
    private NativeExpressAD mADManager;
    private HashMap<Integer, NativeExpressADView> mAdViewPositionMap = new HashMap<>();

    public OnLoadListener onLoadListener;
    public int count;
    public int step;
    public int start;
    public Context context;
    public GdtNativeManager(OnLoadListener onLoadListener, int start, int step, int count ) {
        this.onLoadListener = onLoadListener;
        this.start = start;
        this.step = step;
        this.count = count;
    }
    public int getAdSize(){
        return mAdViewPositionMap.size();
    }
    //text list
    public void loadData(Context context){
        ADSize adSize = new ADSize(ADSize.FULL_WIDTH, ADSize.AUTO_HEIGHT); // 消息流中用AUTO_HEIGHT
        mADManager = new NativeExpressAD(context, adSize, Constants.APPID, Constants.NativeExpressPosID, this);
        mADManager.loadAD(count);
        this.context = context;
    }
    private void notifyLoad(){
        if(onLoadListener!=null){
            onLoadListener.onload(mAdViewPositionMap);
        }
    }
    //video list
    public void loadData2(Context context){
        ADSize adSize = new ADSize(ADSize.FULL_WIDTH, ADSize.AUTO_HEIGHT); // 消息流中用AUTO_HEIGHT
        mADManager = new NativeExpressAD(context, adSize, Constants.APPID, Constants.NativeExpressPosID_2, this);
        mADManager.loadAD(count);
        this.context = context;
    }
    //video list
    public void loadDataVideoDetail(Context context){
        ADSize adSize = new ADSize(ADSize.FULL_WIDTH, ADSize.AUTO_HEIGHT); // 消息流中用AUTO_HEIGHT
        mADManager = new NativeExpressAD(context, adSize, Constants.APPID, Constants.NativeExpressPosID_2, this);
//        mADManager.setVideoOption(new VideoOption.Builder()
//                .setAutoPlayPolicy(VideoOption.AutoPlayPolicy.WIFI) // 设置什么网络环境下可以自动播放视频
//                .setAutoPlayMuted(false) // 设置自动播放视频时，是否静音
//                .build()); // setVideoOption是可选的，开发者可根据需要选择是否配置
        mADManager.loadAD(count);
        this.context = context;
    }
    //banner tooler
    public void loadDataBanner(Context context){
        ADSize adSize = new ADSize(ADSize.FULL_WIDTH, ADSize.AUTO_HEIGHT); // 消息流中用AUTO_HEIGHT
        mADManager = new NativeExpressAD(context, adSize, Constants.APPID, Constants.NativeExpressPosID_2, this);
        mADManager.loadAD(count);
        this.context = context;
    }
    //customer list
    public void loadDataCustomer(Context context){
        ADSize adSize = new ADSize(ADSize.FULL_WIDTH, ADSize.AUTO_HEIGHT); // 消息流中用AUTO_HEIGHT
        mADManager = new NativeExpressAD(context, adSize, Constants.APPID, Constants.NativeExpressPosID, this);
        mADManager.loadAD(count);
        this.context = context;
    }
    public boolean showAds(int position, ViewGroup group){
        if(mAdViewPositionMap.get(position)!=null){
            group.setVisibility(View.VISIBLE);
            group.removeAllViews();
            ViewGroup parent = (ViewGroup) mAdViewPositionMap.get(position).getParent();
            if (parent != null) {
                parent.removeAllViews();
            }
            group.addView(mAdViewPositionMap.get(position));
            mAdViewPositionMap.get(position).render(); //
            return true;
        }else{
            return false;
        }
    }
    @Override
    public void onNoAD(AdError adError) {
        Log.i(
                TAG,
                String.format("onNoAD, error code: %d, error msg: %s", adError.getErrorCode(),
                        adError.getErrorMsg()));
        notifyLoad();
    }

    @Override
    public void onADLoaded(List<NativeExpressADView> adList) {
        Log.i(TAG, "onADLoaded: " + adList.size());
        mAdViewPositionMap.clear();
        for (int i = 0; i < adList.size(); i++) {
            int position = start + step * i;
            Log.i(TAG, "onADLoaded: ad on " + position);
            NativeExpressADView view = adList.get(i);
            if (view.getBoundData().getAdPatternType() == AdPatternType.NATIVE_VIDEO) {
                view.setMediaListener(mediaListener);
            }
            Log.i(TAG, "ad load[" + i + "]: " + getAdInfo(view));
            mAdViewPositionMap.put(position, view); // 把每个广告在列表中位置记录下来
        }
        notifyLoad();
    }

    @Override
    public void onRenderFail(NativeExpressADView adView) {
        Log.i(TAG, "onRenderFail: " + adView.toString());
    }

    @Override
    public void onRenderSuccess(NativeExpressADView adView) {
        Log.i(TAG, "onRenderSuccess: " + adView.toString() + ", adInfo: " + getAdInfo(adView));
    }

    @Override
    public void onADExposure(NativeExpressADView adView) {
        Log.i(TAG, "onADExposure: " + adView.toString());
    }

    @Override
    public void onADClicked(NativeExpressADView adView) {
        Log.i(TAG, "onADClicked: " + adView.toString());
//        String msg = context.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
//        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        ScoreTask.start(context, Constants.ADS_SHOW_CLICK);
    }

    @Override
    public void onADClosed(NativeExpressADView adView) {
        Log.i(TAG, "onADClosed: " + adView.toString());
        ViewGroup parent = (ViewGroup) adView.getParent();
        if (parent != null) {
            parent.removeAllViews();
        }
    }

    @Override
    public void onADLeftApplication(NativeExpressADView adView) {
        Log.i(TAG, "onADLeftApplication: " + adView.toString());
    }

    @Override
    public void onADOpenOverlay(NativeExpressADView adView) {
        Log.i(TAG, "onADOpenOverlay: " + adView.toString());
    }

    @Override
    public void onADCloseOverlay(NativeExpressADView adView) {
        Log.i(TAG, "onADCloseOverlay");
    }

    private String getAdInfo(NativeExpressADView nativeExpressADView) {
        AdData adData = nativeExpressADView.getBoundData();
        if (adData != null) {
            StringBuilder infoBuilder = new StringBuilder();
            infoBuilder.append("title:").append(adData.getTitle()).append(",")
                    .append("desc:").append(adData.getDesc()).append(",")
                    .append("patternType:").append(adData.getAdPatternType());
            if (adData.getAdPatternType() == AdPatternType.NATIVE_VIDEO) {
                infoBuilder.append(", video info: ")
                        .append(getVideoInfo(adData.getProperty(AdData.VideoPlayer.class)));
            }
            return infoBuilder.toString();
        }
        return null;
    }

    private String getVideoInfo(AdData.VideoPlayer videoPlayer) {
        if (videoPlayer != null) {
            StringBuilder videoBuilder = new StringBuilder();
            videoBuilder.append("state:").append(videoPlayer.getVideoState()).append(",")
                    .append("duration:").append(videoPlayer.getDuration()).append(",")
                    .append("position:").append(videoPlayer.getCurrentPosition());
            return videoBuilder.toString();
        }
        return null;
    }

    private NativeExpressMediaListener mediaListener = new NativeExpressMediaListener() {
        @Override
        public void onVideoInit(NativeExpressADView nativeExpressADView) {
            Log.i(TAG, "onVideoInit: "
                    + getVideoInfo(nativeExpressADView.getBoundData().getProperty(AdData.VideoPlayer.class)));
        }

        @Override
        public void onVideoLoading(NativeExpressADView nativeExpressADView) {
            Log.i(TAG, "onVideoLoading: "
                    + getVideoInfo(nativeExpressADView.getBoundData().getProperty(AdData.VideoPlayer.class)));
        }

        @Override
        public void onVideoReady(NativeExpressADView nativeExpressADView, long l) {
            Log.i(TAG, "onVideoReady: "
                    + getVideoInfo(nativeExpressADView.getBoundData().getProperty(AdData.VideoPlayer.class)));
        }

        @Override
        public void onVideoStart(NativeExpressADView nativeExpressADView) {
            Log.i(TAG, "onVideoStart: "
                    + getVideoInfo(nativeExpressADView.getBoundData().getProperty(AdData.VideoPlayer.class)));
        }

        @Override
        public void onVideoPause(NativeExpressADView nativeExpressADView) {
            Log.i(TAG, "onVideoPause: "
                    + getVideoInfo(nativeExpressADView.getBoundData().getProperty(AdData.VideoPlayer.class)));
        }

        @Override
        public void onVideoComplete(NativeExpressADView nativeExpressADView) {
            Log.i(TAG, "onVideoComplete: "
                    + getVideoInfo(nativeExpressADView.getBoundData().getProperty(AdData.VideoPlayer.class)));
        }

        @Override
        public void onVideoError(NativeExpressADView nativeExpressADView, AdError adError) {
            Log.i(TAG, "onVideoError");
        }

        @Override
        public void onVideoPageOpen(NativeExpressADView nativeExpressADView) {
            Log.i(TAG, "onVideoPageOpen");
        }

        @Override
        public void onVideoPageClose(NativeExpressADView nativeExpressADView) {
            Log.i(TAG, "onVideoPageClose");
        }
    };

    public interface OnLoadListener {
        public void onload(HashMap<Integer, NativeExpressADView> mAdViewPositionMap);
    }
}
