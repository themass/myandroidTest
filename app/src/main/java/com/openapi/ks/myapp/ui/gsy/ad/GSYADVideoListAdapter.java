package com.openapi.ks.myapp.ui.gsy.ad;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.ui.gsy.ListADVideoPlayer;
import com.openapi.ks.myapp.ui.gsy.SampleCoverVideo;
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack;
import com.shuyu.gsyvideoplayer.utils.GSYVideoHelper;
import com.shuyu.gsyvideoplayer.video.GSYADVideoPlayer;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

import java.util.List;

import butterknife.BindView;


public class GSYADVideoListAdapter extends BaseRecyclerViewAdapter<GSYADVideoListAdapter.GSYRecyclerItemViewHolder, RecommendVo> {
    public final static String TAG = "RecyclerView2List";
    private GSYVideoHelper smallVideoHelper;

    private GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder;

    public GSYADVideoListAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, OnRecyclerViewItemClickListener<RecommendVo> listener) {
        super(context, recyclerView, data, listener);
    }

    public void setVideoHelper(GSYVideoHelper smallVideoHelper, GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder) {
        this.smallVideoHelper = smallVideoHelper;
        this.gsySmallVideoHelperBuilder = gsySmallVideoHelperBuilder;
    }

    @Override
    public GSYRecyclerItemViewHolder onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.gsy_list_video_item_ad, parent, false);
        return new GSYRecyclerItemViewHolder(this, view, this, this);
    }

    @Override
    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        final GSYRecyclerItemViewHolder holder = (GSYRecyclerItemViewHolder)h;

        //多个播放时必须在setUpLazy、setUp和getGSYVideoManager()等前面设置
        holder.gsyVideoPlayer.setPlayTag(TAG);
        holder.gsyVideoPlayer.setPlayPosition(position);
        boolean isPlaying = holder.gsyVideoPlayer.getCurrentPlayer().isInPlayingState();

        if (!isPlaying) {
            holder.gsyVideoPlayer.setUpLazy(data.get(position).actionUrl, false, null, null, data.get(position).title);
        }

        boolean isADPlaying = holder.adVideoPlayer.getCurrentPlayer().isInPlayingState();
        if (!isADPlaying) {
            holder.adVideoPlayer.setUpLazy("http://7xjmzj.com1.z0.glb.clouddn.com/20171026175005_JObCxCE2.mp4", false, null, null, "这是title");
        }
        holder.gsyVideoPlayer.loadCoverImage(data.get(position).img, R.drawable.img_bg);

        //增加title
        holder.gsyVideoPlayer.getTitleTextView().setVisibility(View.VISIBLE);
        //设置返回键
        holder.gsyVideoPlayer.getBackButton().setVisibility(View.GONE);

        //设置全屏按键功能
        holder.gsyVideoPlayer.getFullscreenButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resolveFullBtn(holder.gsyVideoPlayer);
            }
        });
        holder.adVideoPlayer.getFullscreenButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resolveFullBtn(holder.adVideoPlayer);
            }
        });
        holder.gsyVideoPlayer.setRotateViewAuto(false);
        holder.adVideoPlayer.setRotateViewAuto(false);
        holder.gsyVideoPlayer.setLockLand(true);
        holder.adVideoPlayer.setLockLand(true);
        holder.gsyVideoPlayer.setReleaseWhenLossAudio(false);
        holder.adVideoPlayer.setReleaseWhenLossAudio(false);
        holder.gsyVideoPlayer.setShowFullAnimation(false);
        holder.adVideoPlayer.setShowFullAnimation(false);
        holder.gsyVideoPlayer.setIsTouchWiget(false);
        holder.adVideoPlayer.setIsTouchWiget(false);

        holder.gsyVideoPlayer.setNeedLockFull(true);

        holder.gsyVideoPlayer.setVideoAllCallBack(new GSYSampleCallBack() {

            @Override
            public void onClickStartIcon(String url, Object... objects) {
                super.onClickStartIcon(url, objects);
                if (holder.adVideoPlayer.getGSYVideoManager().listener() != null) {
                    holder.adVideoPlayer.getGSYVideoManager().listener().onAutoCompletion();
                }
            }

            @Override
            public void onPrepared(String url, Object... objects) {
                super.onPrepared(url, objects);
                if (isNeedAdOnStart()) {
                    holder.gsyVideoPlayer.getCurrentPlayer().onVideoPause();
                    startAdPlay(holder.adVideoPlayer, holder.gsyVideoPlayer);
                }
            }

            @Override
            public void onQuitFullscreen(String url, Object... objects) {
                super.onQuitFullscreen(url, objects);
            }

            @Override
            public void onEnterFullscreen(String url, Object... objects) {
                super.onEnterFullscreen(url, objects);
                holder.gsyVideoPlayer.getCurrentPlayer().getTitleTextView().setText((String) objects[0]);
            }

            @Override
            public void onAutoComplete(String url, Object... objects) {
                super.onAutoComplete(url, objects);
            }
        });

        holder.adVideoPlayer.setVideoAllCallBack(new GSYSampleCallBack() {


            @Override
            public void onAutoComplete(String url, Object... objects) {
                //广告结束，释放
                holder.adVideoPlayer.getCurrentPlayer().release();
                holder.adVideoPlayer.onVideoReset();
                holder.adVideoPlayer.setVisibility(View.GONE);

                //开始播放原视频，根据是否处于全屏状态判断
                int playPosition = holder.gsyVideoPlayer.getGSYVideoManager().getPlayPosition();
                if (position == playPosition) {
                    holder.gsyVideoPlayer.getCurrentPlayer().startAfterPrepared();
                }

                if (holder.adVideoPlayer.getCurrentPlayer().isIfCurrentIsFullscreen()) {
                    holder.adVideoPlayer.removeFullWindowViewOnly();
                    if (!holder.gsyVideoPlayer.getCurrentPlayer().isIfCurrentIsFullscreen()) {
                        resolveFullBtn(holder.gsyVideoPlayer);
                        holder.gsyVideoPlayer.setSaveBeforeFullSystemUiVisibility(holder.adVideoPlayer.getSaveBeforeFullSystemUiVisibility());
                    }
                }
            }

            @Override
            public void onQuitFullscreen(String url, Object... objects) {
                //退出全屏逻辑
                if (holder.gsyVideoPlayer.isIfCurrentIsFullscreen()) {
                    holder.gsyVideoPlayer.onBackFullscreen();
                }
            }

        });

    }
    /**
     * 全屏幕按键处理
     */
    private void resolveFullBtn(final StandardGSYVideoPlayer standardGSYVideoPlayer) {
        standardGSYVideoPlayer.startWindowFullscreen(context, false, true);
    }


    /**
     * 显示播放广告
     */
    public void startAdPlay(GSYADVideoPlayer gsyadVideoPlayer, StandardGSYVideoPlayer normalPlayer) {
        gsyadVideoPlayer.setVisibility(View.VISIBLE);
        gsyadVideoPlayer.startPlayLogic();
        if (normalPlayer.getCurrentPlayer().isIfCurrentIsFullscreen()) {
            resolveFullBtn(gsyadVideoPlayer);
            gsyadVideoPlayer.setSaveBeforeFullSystemUiVisibility(normalPlayer.getSaveBeforeFullSystemUiVisibility());
        }
    }

    public boolean isNeedAdOnStart() {
        return true;
    }
    public static class GSYRecyclerItemViewHolder extends BaseRecyclerViewHolder<RecommendVo> {

        public final static String TAG = "RecyclerView2List";

        protected Context context;
        @Nullable
        @BindView(R.id.video_item_player)
        SampleCoverVideo gsyVideoPlayer;

        @Nullable
        @BindView(R.id.video_ad_player)
        ListADVideoPlayer adVideoPlayer;

        public GSYRecyclerItemViewHolder(GSYADVideoListAdapter adapter, View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            context = adapter.context;
        }
    }
}