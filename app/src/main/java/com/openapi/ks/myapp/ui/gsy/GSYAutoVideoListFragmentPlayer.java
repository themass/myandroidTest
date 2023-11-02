package com.openapi.ks.myapp.ui.gsy;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.InfoListVo;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.ui.base.features.BasePullLoadbleFragment;
import com.openapi.ks.myapp.ui.sound.media.JzvdStdTinyWindow;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.Debuger;
import com.shuyu.gsyvideoplayer.utils.GSYVideoHelper;
import com.shuyu.gsyvideoplayer.video.NormalGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer;

import cn.jzvd.Jzvd;

/**
 * A placeholder fragment containing a simple view.
 */
public class GSYAutoVideoListFragmentPlayer extends BasePullLoadbleFragment<RecommendVo> {
    private GSYVideoListAdapterPlayer videoListAdapter;
    private static final String TAG="avvideo";
    private String channel;
    private boolean isSmall = false;
    @Override
    protected BaseRecyclerViewAdapter getAdapter(){
        videoListAdapter = new GSYVideoListAdapterPlayer(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
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
        pullView.getRecyclerView().addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {//这个的本质是gotoThisJzvd，不管他是不是原来的容器
                SampleCoverVideo mGsyVideoPlayer = view.findViewById(R.id.video_item_player);
                mGsyVideoPlayer.hideSmallVideo();
                isSmall = false;
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
                SampleCoverVideo mGsyVideoPlayer = view.findViewById(R.id.video_item_player);
                if (mGsyVideoPlayer.getCurrentState() == GSYVideoPlayer.CURRENT_STATE_PLAYING && !isSmall) {
                    int size = CommonUtil.dip2px(getActivity(), 150);
                    mGsyVideoPlayer.showSmallVideo(new Point(size, size), true, true);
                    isSmall = true;
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
        GSYVideoManager.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        GSYVideoManager.releaseAllVideos();
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
