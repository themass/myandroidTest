package com.openapi.ks.myapp.video.list;//package com.openapi.ks.myapp.ui.gsy;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.openapi.feature.video.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.data.VideoUtil;
import com.shuyu.gsyvideoplayer.utils.GSYVideoHelper;

import java.util.List;

/**
 * 视频列表的适配器。卡片外观由 Compose 绘制（见 [VideoListItemComposeKt]），
 * 播放器仍然是 GSY 的 [GSYVideoHelper]：它把播放器挂进卡片里的原生 [FrameLayout]，并控制播放按钮。
 */
public class GSYVideoListAdapter extends BaseRecyclerViewAdapter<GSYVideoListAdapter.GSYRecyclerItemViewHolder, RecommendVo> {
    public final static String TAG = "RecyclerView2List";
    private GSYVideoHelper smallVideoHelper;

    private GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder;

    public GSYVideoListAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, OnRecyclerViewItemClickListener<RecommendVo> listener) {
        super(context, recyclerView, data, listener);
    }

    public void setVideoHelper(GSYVideoHelper smallVideoHelper, GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder) {
        this.smallVideoHelper = smallVideoHelper;
        this.gsySmallVideoHelperBuilder = gsySmallVideoHelperBuilder;
    }

    @Override
    public GSYRecyclerItemViewHolder onCreateViewHolderData(ViewGroup parent, int viewType) {
        ComposeView view = new ComposeView(context);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new GSYRecyclerItemViewHolder(this, view, this, this);
    }

    @Override
    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        GSYRecyclerItemViewHolder recyclerItemViewHolder = (GSYRecyclerItemViewHolder) h;
        recyclerItemViewHolder.setVideoHelper(smallVideoHelper, gsySmallVideoHelperBuilder);
        recyclerItemViewHolder.onBind(position, data.get(position));
    }

    public static class GSYRecyclerItemViewHolder extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<RecommendVo> {

        public final static String TAG = "RecyclerView2List";

        protected Context context;
        /** GSY 播放器挂载的容器（原生 View，由 Compose 通过 AndroidView 嵌入）。 */
        @Nullable
        FrameLayout listItemContainer;
        /** 播放按钮（原生 View，GSY 控制显隐）。 */
        @Nullable
        ImageView listItemBtn;
        private final VideoListItemState itemState = new VideoListItemState();

        ImageView imageView;
        private GSYVideoHelper smallVideoHelper;
        private GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder;
        private GSYVideoListAdapter adapter;

        public GSYRecyclerItemViewHolder(GSYVideoListAdapter adapter, ComposeView itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            context = adapter.context;
            listItemContainer = new FrameLayout(context);
            listItemBtn = createPlayButton(context);
            VideoListItemComposeKt.bindVideoListItem(itemView, itemState, listItemContainer, listItemBtn);
            imageView = new ImageView(context);
            this.adapter = adapter;
        }

        public void onBind(final int position, RecommendVo videoModel) {
            //增加封面
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(context).load(videoModel.img).into(imageView);
            smallVideoHelper.addVideoPlayer(position, imageView, TAG, listItemContainer, listItemBtn);
            smallVideoHelper.getGsyVideoPlayer().getTitleTextView().setVisibility(View.VISIBLE);
            itemState.setTitle(videoModel.title != null ? videoModel.title : "");
            boolean hasDesc = videoModel.desc != null && !videoModel.desc.trim().isEmpty();
            itemState.setDesc(hasDesc ? videoModel.desc.trim() : "");
            listItemBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    smallVideoHelper.setPlayPositionAndTag(position, TAG);
                    adapter.notifyDataSetChanged();
                    gsySmallVideoHelperBuilder
                            .setVideoTitle(videoModel.title)
                            .setUrl(VideoUtil.buildPlayUrl(videoModel))
                            .setMapHeadData(VideoUtil.headersForRecommend(videoModel));
                    smallVideoHelper.startPlay();
                    //必须在startPlay之后设置才能生效
                    //                listVideoUtil.getGsyVideoPlayer().getTitleTextView().setVisibility(View.VISIBLE);
                }
            });
        }


        public void setVideoHelper(GSYVideoHelper smallVideoHelper, GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder) {
            this.smallVideoHelper = smallVideoHelper;
            this.gsySmallVideoHelperBuilder = gsySmallVideoHelperBuilder;
        }

        /** 与原 XML 中的播放按钮一致：圆形底、14dp 内边距、系统播放图标。 */
        private static ImageView createPlayButton(Context context) {
            ImageView btn = new ImageView(context);
            btn.setBackgroundResource(R.drawable.bg_video_play_circle);
            btn.setImageResource(android.R.drawable.ic_media_play);
            int pad = Math.round(14 * context.getResources().getDisplayMetrics().density);
            btn.setPadding(pad, pad, pad, pad);
            btn.setContentDescription(context.getString(R.string.video));
            return btn;
        }
    }
}
