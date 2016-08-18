package com.timeline.vpn.adapter;

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
import com.timeline.vpn.bean.vo.RecommendVo;

import java.util.List;
import java.util.Random;

import butterknife.Bind;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexRecommendAdapter<NaviItemViewHolder> extends BasePhotoFlowRecycleViewAdapter implements View.OnClickListener{
    private static  final Random random = new Random();
    private ItemClickListener listener;
    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data,ItemClickListener listener) {
        super(context, recyclerView, data);
        this.listener = listener;
    }
    @Override
    public int getItemViewType(int position) {
        RecommendVo vo = (RecommendVo)data.get(position);
        return 0;
    }
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(context).inflate(R.layout.tab_index_item_recommend, parent, false);
        return new IndexRecommendAdapter.NaviItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(BaseViewHolder holder, int position) {
        RecommendVo vo = (RecommendVo)data.get(position);
        IndexRecommendAdapter.NaviItemViewHolder viewHolder = (IndexRecommendAdapter.NaviItemViewHolder)holder;
        super.onBindViewHolder(holder, position);
        holder.itemView.setTag(position);
        holder.itemView.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if(listener != null){
            listener.onItemClick(v,(int)v.getTag());
        }
    }

    @Override
    protected void animatePhoto(BaseViewHolder viewHolder, long animationDelay, int position) {
        IndexRecommendAdapter.NaviItemViewHolder holder = (IndexRecommendAdapter.NaviItemViewHolder)viewHolder;
        RecommendVo vo = (RecommendVo)data.get(position);
        holder.ivTitle.setText(vo.title);
        Glide.with(context)
                .load(vo.img)
                .placeholder(R.drawable.recommand_default)
                .crossFade()
                .into(holder.ivPhoto);
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
    public  interface ItemClickListener{
        public void onItemClick(View v, int position);
    }
}
