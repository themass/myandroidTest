package com.openapi.ks.myapp.ui.gsy.ad;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

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
import com.openapi.ks.myapp.ui.gsy.GSYVideoListAdapter;
import com.openapi.ks.myapp.ui.gsy.SampleCoverVideo;
import com.shuyu.gsyvideoplayer.GSYVideoADManager;
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
public class GSYADAutoVideoListFragment extends BasePullLoadbleFragment<RecommendVo> {
    private GSYADVideoListAdapter videoListAdapter;
    private static final String TAG="avvideo";
    private String channel;
    private boolean isSmall = false;
    private int lastVisibleItem;
    private int firstVisibleItem;
    private LinearLayoutManager linearLayoutManager;
    private GSYVideoHelper smallVideoHelper;
    private GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder;
    @Override
    protected GSYADVideoListAdapter getAdapter(){
        videoListAdapter = new GSYADVideoListAdapter(getActivity(), pullView.getRecyclerView(), infoListVo.voList, this);
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
        pullView.getRecyclerView().addOnScrollListener(new RecyclerView.OnScrollListener() {
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            }

            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                firstVisibleItem = linearLayoutManager.findFirstVisibleItemPosition();
                lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
                //大于0说明有播放
                if (GSYVideoManager.instance().getPlayPosition() >= 0) {
                    //当前播放的位置
                    int position = GSYVideoManager.instance().getPlayPosition();
                    //对应的播放列表TAG
                    if (GSYVideoManager.instance().getPlayTag().equals(GSYADVideoListAdapter.TAG)
                            && (position < firstVisibleItem || position > lastVisibleItem)) {
                        //如果滑出去了上面和下面就是否，和今日头条一样
                        //释放广告和视频
                        if (GSYVideoADManager.instance().listener() != null) {
                            GSYVideoADManager.instance().listener().onAutoCompletion();
                        }
                        GSYVideoADManager.releaseAllVideos();
                        GSYVideoManager.releaseAllVideos();
                        videoListAdapter.notifyDataSetChanged();
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
        GSYVideoADManager.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        GSYVideoManager.releaseAllVideos();
        GSYVideoADManager.releaseAllVideos();
    }
    @Override
    public void onResume() {
        super.onResume();
        GSYVideoManager.onResume();
        GSYVideoADManager.onResume();
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
