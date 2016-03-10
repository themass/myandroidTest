package com.timeline.vpn.ui.adapter;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;

import java.util.List;

import butterknife.Bind;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexRecommendAdapter<String,NaviItemViewHolder> extends BasePhotoFlowRecycleViewAdapter {
    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List data) {
        super(context, recyclerView, data);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(context).inflate(R.layout.tab_index_item_recommend, parent, false);
        return new IndexRecommendAdapter.NaviItemViewHolder(view);
    }
    @Override
    protected void animatePhoto(BaseViewHolder viewHolder, long animationDelay, int position) {
        IndexRecommendAdapter.NaviItemViewHolder holder = (IndexRecommendAdapter.NaviItemViewHolder)viewHolder;
        LogUtil.i("postanimatePhoto = " + position + " h=" + holder.cardView.getHeight());
        holder.ivTitle.setText("p = " + position + " h=" + holder.cardView.getHeight());
        Glide.with(context)
                .load(data.get(position))
                .placeholder(R.color.tab_indicator_color)
                .crossFade()
                .into(holder.ivPhoto);
//        holder.cardView.setScaleY(0);
//        holder.cardView.setScaleX(0);
//        holder.cardView.animate()
//                .scaleY(1)
//                .scaleX(1)
//                .setDuration(200)
//                .setInterpolator(INTERPOLATOR)
//                .setStartDelay(animationDelay)
//                .start();

    }

    static class NaviItemViewHolder extends BasePhotoFlowRecycleViewAdapter.BaseViewHolder {
        @Bind(R.id.card_view)
        CardView cardView;
        @Bind(R.id.tv_title)
        TextView ivTitle;
        @Bind(R.id.iv_photo)
        ImageView ivPhoto;
        public NaviItemViewHolder(View view) {
            super(view);
        }
    }
}
