package com.openapi.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.VideoListAdapter;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.myapp.ui.sound.media.JzvdStdTinyWindow;

import cn.jzvd.Jzvd;

/**
 * A placeholder fragment containing a simple view.
 */
public class AutoVideoListFragment extends BasePullLoadbleFragment<RecommendVo> {
    private VideoListAdapter videoListAdapter;
    private static final String TAG="avvideo";
    private String channel;
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
        channel = (String) getSerializable();
//        recyclerView.addOnScrollListener
        pullView.getRecyclerView().addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
//            @Override
//            public void onChildViewAttachedToWindow(View view) {
//                LogUtil.i("onChildViewAttachedToWindow  "+ Jzvd.CURRENT_JZVD.screen+"--");
//                if (Jzvd.CURRENT_JZVD != null && Jzvd.CURRENT_JZVD.screen == Jzvd.SCREEN_TINY) {
//                    Jzvd jzvd = view.findViewById(R.id.videoplayer);
//                    if(jzvd.jzDataSource.containsTheUrl(Jzvd.CURRENT_JZVD.jzDataSource.getCurrentUrl())){
//                        Jzvd.backPress();
//                    }
//                }
//            }
//
//            @Override
//            public void onChildViewDetachedFromWindow(View view) {
//                LogUtil.i("onChildViewDetachedFromWindow  "+ Jzvd.CURRENT_JZVD.screen+"--");
////                if (Jzvd.CURRENT_JZVD != null && Jzvd.CURRENT_JZVD.screen != Jzvd.SCREEN_TINY) {
////                    Jzvd videoPlayer = Jzvd.CURRENT_JZVD;
////                    if (((ViewGroup) view).indexOfChild(videoPlayer) != -1 && videoPlayer.state == Jzvd.STATE_PLAYING) {
////                        ((JzvdStdTinyWindow) Jzvd.CURRENT_JZVD).gotoTinyScreen();
////                    }
////                }
//                Jzvd jzvd = view.findViewById(R.id.videoplayer);
//                if (jzvd != null && Jzvd.CURRENT_JZVD != null &&
//                        jzvd.jzDataSource.containsTheUrl(Jzvd.CURRENT_JZVD.jzDataSource.getCurrentUrl())) {
//                    if (Jzvd.CURRENT_JZVD != null && Jzvd.CURRENT_JZVD.screen != Jzvd.SCREEN_FULLSCREEN) {
//                        Jzvd.releaseAllVideos();
//                    }
//                }
//            }
//        });
    @Override
    public void onChildViewAttachedToWindow(View view) {//这个的本质是gotoThisJzvd，不管他是不是原来的容器
        //如果这个容器中jzvd的url是currentJzvd的url，那么直接回到这里，不用管其他的
        JzvdStdTinyWindow jzvd = view.findViewById(R.id.videoplayer);
        JzvdStdTinyWindow currentJzvd = (JzvdStdTinyWindow) Jzvd.CURRENT_JZVD;
        if (jzvd != null && currentJzvd != null &&
                jzvd.jzDataSource.containsTheUrl(Jzvd.CURRENT_JZVD.jzDataSource.getCurrentUrl())
                && Jzvd.CURRENT_JZVD.state == Jzvd.STATE_PLAYING) {
            ViewGroup vp = (ViewGroup) jzvd.getParent();
            vp.removeAllViews();
            ((ViewGroup) currentJzvd.getParent()).removeView(currentJzvd);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            vp.addView(currentJzvd, lp);
            currentJzvd.setScreenNormal();
            Jzvd.CONTAINER_LIST.pop();
        }
    }

        @Override
        public void onChildViewDetachedFromWindow(View view) {
            JzvdStdTinyWindow jzvd = view.findViewById(R.id.videoplayer);
            if (jzvd != null && Jzvd.CURRENT_JZVD != null &&
                    jzvd.jzDataSource.containsTheUrl(Jzvd.CURRENT_JZVD.jzDataSource.getCurrentUrl())
                    && Jzvd.CURRENT_JZVD.screen != Jzvd.SCREEN_TINY) {
                if (Jzvd.CURRENT_JZVD.state == Jzvd.STATE_PAUSE) {
                    Jzvd.releaseAllVideos();
                } else {
                    ((JzvdStdTinyWindow) Jzvd.CURRENT_JZVD).gotoTinyScreen();
                }
            }
        }
    });

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
}
