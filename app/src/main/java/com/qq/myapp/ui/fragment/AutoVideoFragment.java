package com.qq.myapp.ui.fragment;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.kb.R;
import com.sspacee.common.util.LogUtil;

import com.sspacee.yewu.ads.base.GdtNativeManager;
import com.qq.myapp.adapter.VideoListAdapter;
import com.qq.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.qq.myapp.bean.vo.InfoListVo;
import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.ui.base.features.BasePullLoadbleFragment;

import java.util.HashMap;

import cn.jzvd.Jzvd;

/**
 * A placeholder fragment containing a simple view.
 */
public class AutoVideoFragment extends BasePullLoadbleFragment<RecommendVo> implements GdtNativeManager.OnLoadListener{
    private VideoListAdapter videoListAdapter;
    private static final String TAG="avvideo";
    private String channel;
    GdtNativeManager gdtNativeManager =new GdtNativeManager(this,Constants.FIRST_AD_POSITION,Constants.ITEMS_PER_AD_THREE,Constants.AD_COUNT);;

    @Override
    protected BaseRecyclerViewAdapter getAdapter(){
        videoListAdapter = new VideoListAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this,gdtNativeManager);
        return videoListAdapter;
    }
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_video_fragment, parent);
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        channel = (String) getSerializable();
//        recyclerView.addOnScrollListener
        pullView.getRecyclerView().addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
//                if (JzvdMgr.getCurrentJzvd() != null && JzvdMgr.getCurrentJzvd().currentScreen == Jzvd.SCREEN_WINDOW_TINY) {
//                    Jzvd videoPlayer = (Jzvd )view.findViewById(R.id.videoplayer);
//                    if (JZUtils.getCurrentFromDataSource(videoPlayer.jzDataSource, videoPlayer.i).equals(JZMediaManager.getCurrentDataSource())) {
//                        Jzvd.backPress();
//                    }
//                }
                Jzvd.onChildViewAttachedToWindow(view, R.id.videoplayer);
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
//                if (JzvdMgr.getCurrentJzvd() != null && JzvdMgr.getCurrentJzvd().currentScreen != Jzvd.SCREEN_WINDOW_TINY) {
//                    Jzvd videoPlayer = JzvdMgr.getCurrentJzvd();
//                    if (((ViewGroup) view).indexOfChild(videoPlayer) != -1 && videoPlayer.currentState == Jzvd.CURRENT_STATE_PLAYING) {
//                        videoPlayer.startWindowTiny();
//                    }
//                }
                Jzvd.onChildViewDetachedFromWindow(view);
            }
        });
        gdtNativeManager.loadData2(getActivity());
    }

    @Override
    protected boolean showSearchView() {
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        Jzvd.goOnPlayOnPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Jzvd.releaseAllVideos();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        //你的代码
        super.onConfigurationChanged(newConfig);
        LogUtil.i("onConfigurationChanged");
    }
    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_VIDEO_CHANNEL_LIST_URL, infoListVo.pageNum,channel,keyword), RecommendVo.class, TAG);
    }

    public void onload(HashMap<Integer, NativeExpressADView> mAdViewPositionMap){
        videoListAdapter.notifyDataSetChanged();
    }
}
