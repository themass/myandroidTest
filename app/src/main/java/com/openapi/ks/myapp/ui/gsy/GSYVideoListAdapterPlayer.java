package com.openapi.ks.myapp.ui.gsy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.shuyu.gsyvideoplayer.utils.GSYVideoHelper;
import com.shuyu.gsyvideoplayer.video.NormalGSYVideoPlayer;

import java.util.List;

import butterknife.BindView;


public class GSYVideoListAdapterPlayer extends BaseRecyclerViewAdapter<GSYVideoListAdapterPlayer.GSYRecyclerItemViewHolder, RecommendVo> {
    public final static String TAG = "RecyclerView2List";

    public GSYVideoListAdapterPlayer(Context context, RecyclerView recyclerView, List<RecommendVo> data, OnRecyclerViewItemClickListener<RecommendVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public GSYRecyclerItemViewHolder onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.gsy_item_player, parent, false);
        return new GSYRecyclerItemViewHolder(this, view, this, this);
    }

    @Override
    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        GSYRecyclerItemViewHolder recyclerItemViewHolder = (GSYRecyclerItemViewHolder) h;
        recyclerItemViewHolder.onBind(position, data.get(position));
    }

    public static class GSYRecyclerItemViewHolder extends BaseRecyclerViewHolder<RecommendVo> {

        public final static String TAG = "RecyclerView2List";

        protected Context context;
        @Nullable
        @BindView(R.id.video_item_player)
        SampleCoverVideo gsyVideoPlayer;

        public GSYRecyclerItemViewHolder(GSYVideoListAdapterPlayer adapter, View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            context = adapter.context;
        }

        public void onBind(final int position, RecommendVo videoModel) {
            //增加封面
            //增加封面
            gsyVideoPlayer.loadCoverImage(videoModel.img, R.drawable.img_bg);
            //增加title
            gsyVideoPlayer.setUpLazy(videoModel.actionUrl, true, null, null, videoModel.title);
            //增加title
            gsyVideoPlayer.getTitleTextView().setVisibility(View.VISIBLE);
            //设置返回键
            gsyVideoPlayer.getBackButton().setVisibility(View.GONE);
            //设置全屏按键功能
            gsyVideoPlayer.getFullscreenButton().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    gsyVideoPlayer.startWindowFullscreen(context, false, true);
                }
            });
            //防止错位设置
            gsyVideoPlayer.setPlayTag(TAG);
            gsyVideoPlayer.setLockLand(true);
            gsyVideoPlayer.setPlayPosition(position);
            //是否根据视频尺寸，自动选择竖屏全屏或者横屏全屏，这个标志为和 setLockLand 冲突，需要和 orientationUtils 使用
            gsyVideoPlayer.setAutoFullWithSize(false);
            //音频焦点冲突时是否释放
            gsyVideoPlayer.setReleaseWhenLossAudio(false);
            //全屏动画
            gsyVideoPlayer.setShowFullAnimation(true);
            //小屏时不触摸滑动
            gsyVideoPlayer.setIsTouchWiget(false);
        }

    }
}