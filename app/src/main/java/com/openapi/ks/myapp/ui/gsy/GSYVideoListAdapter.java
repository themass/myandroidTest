package com.openapi.ks.myapp.ui.gsy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.openapi.ks.moviefree1.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.shuyu.gsyvideoplayer.utils.GSYVideoHelper;

import java.util.List;

import butterknife.BindView;
import cn.jzvd.JzvdStd;


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
        View view = LayoutInflater.from(context).inflate(R.layout.gsy_list_video_item, parent, false);
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
        @Nullable
        @BindView(R.id.list_item_container)
        FrameLayout listItemContainer;
        @Nullable
        @BindView(R.id.list_item_btn)
        ImageView listItemBtn;
        @Nullable
        @BindView(R.id.list_item_title)
        TextView listItemTitle;

        ImageView imageView;
        private GSYVideoHelper smallVideoHelper;
        private GSYVideoHelper.GSYVideoHelperBuilder gsySmallVideoHelperBuilder;
        private GSYVideoListAdapter adapter;

        public GSYRecyclerItemViewHolder(GSYVideoListAdapter adapter, View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            context = adapter.context;
            imageView = new ImageView(context);
            this.adapter = adapter;
        }

        public void onBind(final int position, RecommendVo videoModel) {
            //增加封面
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(context).load(videoModel.img).into(imageView);
            smallVideoHelper.addVideoPlayer(position, imageView, TAG, listItemContainer, listItemBtn);
            smallVideoHelper.getGsyVideoPlayer().getTitleTextView().setVisibility(View.VISIBLE);
            listItemTitle.setText(videoModel.title);
            listItemBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    smallVideoHelper.setPlayPositionAndTag(position, TAG);
                    adapter.notifyDataSetChanged();
                    gsySmallVideoHelperBuilder.setVideoTitle(videoModel.title).setUrl(videoModel.actionUrl);
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
    }
}