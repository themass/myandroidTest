package com.timeline.sex.ui.fragment;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sspacee.common.util.LogUtil;
import com.timeline.sex.R;
import com.timeline.sex.adapter.VideoListAdapter;
import com.timeline.sex.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.sex.bean.vo.InfoListVo;
import com.timeline.sex.bean.vo.RecommendVo;
import com.timeline.sex.constant.Constants;
import com.timeline.sex.ui.base.features.BasePullLoadbleFragment;

import cn.jzvd.JZMediaManager;
import cn.jzvd.JZUtils;
import cn.jzvd.JZVideoPlayer;
import cn.jzvd.JZVideoPlayerManager;

/**
 * A placeholder fragment containing a simple view.
 */
public class AutoVideoFragment extends BasePullLoadbleFragment<RecommendVo> {
    private VideoListAdapter videoListAdapter;
    private static final String TAG="avvideo";
    @Override
    protected BaseRecyclerViewAdapter getAdapter(){
        videoListAdapter = new VideoListAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
        return videoListAdapter;
    }
    @Override
    protected void onContentViewCreated(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        inflater.inflate(R.layout.layout_video_fragment, parent);
    }
    @Override
    public void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
//        recyclerView.addOnScrollListener
        pullView.getRecyclerView().addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                if (JZVideoPlayerManager.getCurrentJzvd() != null && JZVideoPlayerManager.getCurrentJzvd().currentScreen == JZVideoPlayer.SCREEN_WINDOW_TINY) {
                    JZVideoPlayer videoPlayer = (JZVideoPlayer )view.findViewById(R.id.videoplayer);
                    if (JZUtils.getCurrentUrlFromMap(videoPlayer.urlMap, videoPlayer.currentUrlMapIndex).equals(JZMediaManager.CURRENT_PLAYING_URL)) {
                        JZVideoPlayer.backPress();
                    }
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
                if (JZVideoPlayerManager.getCurrentJzvd() != null && JZVideoPlayerManager.getCurrentJzvd().currentScreen != JZVideoPlayer.SCREEN_WINDOW_TINY) {
                    JZVideoPlayer videoPlayer = JZVideoPlayerManager.getCurrentJzvd();
                    if (((ViewGroup) view).indexOfChild(videoPlayer) != -1 && videoPlayer.currentState == JZVideoPlayer.CURRENT_STATE_PLAYING) {
                        videoPlayer.startWindowTiny();
                    }
                }
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        JZVideoPlayer.goOnPlayOnPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        JZVideoPlayer.releaseAllVideos();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        //你的代码
        super.onConfigurationChanged(newConfig);
        LogUtil.i("onConfigurationChanged");
    }
    @Override
    protected InfoListVo<RecommendVo> loadData(Context context) throws Exception {
        return indexService.getInfoListData(Constants.getUrlWithParam(Constants.API_VIDEO_ITEMS_URL, infoListVo.pageNum), RecommendVo.class, TAG);
    }
}
