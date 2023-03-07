package com.ks.myapp.ui.fragment;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ks.myapp.ui.sound.media.JzvdStdTinyWindow;
import com.ks.myapp.ui.user.SettingActivity;
import com.sspacee.common.util.LogUtil;
import com.ks.sexfree1.R;
import com.ks.myapp.adapter.VideoListAdapter;
import com.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.ks.myapp.bean.vo.InfoListVo;
import com.ks.myapp.bean.vo.RecommendVo;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.ui.base.features.BasePullLoadbleFragment;

import cn.jzvd.JZDataSource;
import cn.jzvd.Jzvd;

/**
 * A placeholder fragment containing a simple view.
 */
public class AutoVideoFragment extends BasePullLoadbleFragment<RecommendVo> {
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
            @Override
            public void onChildViewAttachedToWindow(View view) {
                if (Jzvd.CURRENT_JZVD != null && Jzvd.CURRENT_JZVD.screen == Jzvd.SCREEN_TINY) {
                    Jzvd jzvd = view.findViewById(R.id.videoplayer);
                    if(jzvd.jzDataSource.containsTheUrl(Jzvd.CURRENT_JZVD.jzDataSource.getCurrentUrl())){
                        Jzvd.backPress();
                    }
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
//                if (Jzvd.CURRENT_JZVD != null && Jzvd.CURRENT_JZVD.screen != Jzvd.SCREEN_TINY) {
//                    Jzvd videoPlayer = Jzvd.CURRENT_JZVD;
//                    if (((ViewGroup) view).indexOfChild(videoPlayer) != -1 && videoPlayer.state == Jzvd.STATE_PLAYING) {
//                        ((JzvdStdTinyWindow) Jzvd.CURRENT_JZVD).gotoTinyScreen();
//                    }
//                }
                Jzvd jzvd = view.findViewById(R.id.videoplayer);
                if (jzvd != null && Jzvd.CURRENT_JZVD != null &&
                        jzvd.jzDataSource.containsTheUrl(Jzvd.CURRENT_JZVD.jzDataSource.getCurrentUrl())) {
                    if (Jzvd.CURRENT_JZVD != null && Jzvd.CURRENT_JZVD.screen != Jzvd.SCREEN_FULLSCREEN) {
                        Jzvd.releaseAllVideos();
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
