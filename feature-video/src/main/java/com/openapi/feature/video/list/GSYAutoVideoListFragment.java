package com.openapi.feature.video.list;

import java.util.concurrent.CompletableFuture;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openapi.common.util.log.LogUtil;
import com.openapi.feature.video.R;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.common.net.request.InfoListVo;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.core.legacyui.features.BasePullLoadbleFragment;
import com.openapi.feature.video.page.VideoPlaybackUiHelper;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.Debuger;
import com.shuyu.gsyvideoplayer.utils.GSYVideoHelper;
import com.shuyu.gsyvideoplayer.video.NormalGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer;

/**
 * A placeholder fragment containing a simple view.
 */
public class GSYAutoVideoListFragment extends BasePullLoadbleFragment<RecommendVo> {
    private GSYVideoListAdapter videoListAdapter;
    private static final String TAG = "avvideo";
    private String channel;
    GSYVideoHelper smallVideoHelper;
    GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder;
    int lastVisibleItem;
    int firstVisibleItem;
    LinearLayoutManager linearLayoutManager;

    @Override
    protected BaseRecyclerViewAdapter getAdapter() {
        videoListAdapter = new GSYVideoListAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
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
        linearLayoutManager = new LinearLayoutManager(getContext());
        pullView.getRecyclerView().setLayoutManager(linearLayoutManager);
        smallVideoHelper = new GSYVideoHelper(getContext(), new NormalGSYVideoPlayer(getContext()));
        smallVideoHelper.setFullViewContainer(fullContainer);

        //配置
        gsySmallVideoHelperBuilder = new GSYVideoHelper.GSYVideoHelperBuilder();
        gsySmallVideoHelperBuilder
                .setHideActionBar(true)
                .setHideStatusBar(true)
                .setNeedLockFull(true)
                .setCacheWithPlay(true)
                .setAutoFullWithSize(true)
                .setShowFullAnimation(true)
                .setLockLand(true).setVideoAllCallBack(new GSYSampleCallBack() {
                    @Override
                    public void onPrepared(String url, Object... objects) {
                        super.onPrepared(url, objects);
                    }

                    @Override
                    public void onQuitSmallWidget(String url, Object... objects) {
                        super.onQuitSmallWidget(url, objects);
                        //大于0说明有播放,//对应的播放列表TAG
                        if (smallVideoHelper.getPlayPosition() >= 0 && smallVideoHelper.getPlayTAG().equals(GSYVideoListAdapter.GSYRecyclerItemViewHolder.TAG)) {
                            //当前播放的位置
                            int position = smallVideoHelper.getPlayPosition();
                            //不可视的是时候
                            if ((position < firstVisibleItem || position > lastVisibleItem)) {
                                //释放掉视频
                                smallVideoHelper.releaseVideoPlayer();
                                videoListAdapter.notifyDataSetChanged();
                            }
                        }

                    }

                    @Override
                    public void onEnterFullscreen(String url, Object... objects) {
                        mSearchView.setVisibility(View.GONE);
                    }

                    @Override
                    public void onQuitFullscreen(String url, Object... objects) {
                        if (showSearchView())
                            mSearchView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onPlayError(String url, Object... objects) {
                        super.onPlayError(url, objects);
                        if (getContext() != null) {
                            VideoPlaybackUiHelper.onGsyPlaybackError(getContext(), url);
                        }
                    }
                });

        smallVideoHelper.setGsyVideoOptionBuilder(gsySmallVideoHelperBuilder);

        videoListAdapter.setVideoHelper(smallVideoHelper, gsySmallVideoHelperBuilder);
//        recyclerView.addOnScrollListener
        pullView.getRecyclerView().addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                firstVisibleItem = linearLayoutManager.findFirstVisibleItemPosition();
                lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
                Debuger.printfLog("firstVisibleItem " + firstVisibleItem + " lastVisibleItem " + lastVisibleItem);
                //大于0说明有播放,//对应的播放列表TAG
                if (smallVideoHelper.getPlayPosition() >= 0 && smallVideoHelper.getPlayTAG().equals(GSYVideoListAdapter.GSYRecyclerItemViewHolder.TAG)) {
                    //当前播放的位置
                    int position = smallVideoHelper.getPlayPosition();
                    //不可视的是时候
                    if ((position < firstVisibleItem || position > lastVisibleItem)) {
                        //如果是小窗口就不需要处理；只有正在播放的才弹小窗，暂停或播完的不打扰用户
                        if (!smallVideoHelper.isSmall() && !smallVideoHelper.isFull()
                                && smallVideoHelper.getGsyVideoPlayer().getCurrentState() == GSYVideoPlayer.CURRENT_STATE_PLAYING) {
                            //小窗口，按 16:9 比例，避免视频被拉成正方形
                            int width = CommonUtil.dip2px(getActivity(), 200);
                            int height = width * 9 / 16;
                            //actionbar为true才不会掉下面去
                            smallVideoHelper.showSmallVideo(new Point(width, height), true, true);
                        }
                    } else {
                        if (smallVideoHelper.isSmall()) {
                            smallVideoHelper.smallVideoToNormal();
                        }
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
        GSYVideoManager.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 小窗挂在 Activity 的 DecorView 上，不随列表视图销毁，必须显式释放
        smallVideoHelper.releaseVideoPlayer();
        GSYVideoManager.releaseAllVideos();
    }

    @Override
    protected void freshData(InfoListVo<RecommendVo> data) {
        // 下拉刷新会整体替换列表，小窗记录的是位置索引，旧位置现在可能是另一个视频，直接停掉
        if (smallVideoHelper.getPlayPosition() >= 0) {
            smallVideoHelper.releaseVideoPlayer();
        }
        super.freshData(data);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        //你的代码
        super.onConfigurationChanged(newConfig);
        LogUtil.i("onConfigurationChanged");
    }

    @Override
    protected CompletableFuture<InfoListVo<RecommendVo>> loadData(Context context) {
        return indexService.getInfoListDataAsync(Constants.getUrlWithParam(Constants.API_VIDEO_CHANNEL_LIST_URL, infoListVo.pageNum, channel, keyword), RecommendVo.class, TAG);
    }
}
