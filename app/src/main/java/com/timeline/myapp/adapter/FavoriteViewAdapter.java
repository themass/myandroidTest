package com.timeline.myapp.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.vpn.R;
import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.FavoriteVo;
import com.timeline.myapp.constant.Constants;

import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class FavoriteViewAdapter extends BaseRecyclerViewAdapter<FavoriteViewAdapter.ItemtView, FavoriteVo> {
    public FavoriteViewAdapter(Context context, RecyclerView recyclerView, List<FavoriteVo> data, OnRecyclerViewItemClickListener<FavoriteVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public ItemtView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_channel_items_item, parent, false);
        return new ItemtView(view, this, this);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        ItemtView holder = (ItemtView)h;
        FavoriteVo vo = data.get(position);
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.updateTime);
        holder.ivType.setVisibility(View.VISIBLE);
        if (vo.type == Constants.FavoriteType.SOUND) {
            holder.ivType.setImageResource(R.drawable.song_play_icon);
        }else if (vo.type == Constants.FavoriteType.TEXT) {
            holder.ivType.setImageResource(R.drawable.txt);
        }else if (vo.type == Constants.FavoriteType.IMG) {
            holder.ivType.setImageResource(R.drawable.img);
        } else{
            holder.ivType.setVisibility(View.GONE);
        }
        if(Constants.BANNER_ADS_POS.contains(position)){
            holder.rvAds.setVisibility(View.VISIBLE);
            AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN1);
        }else{
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
        }
    }
    public static class ItemtView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<FavoriteVo> {
        @Nullable
        @BindView(R.id.tv_index)
        TextView tvIndex;
        @Nullable
        @BindView(R.id.tv_name)
        TextView tvName;
        @Nullable
        @BindView(R.id.tv_date)
        TextView tvDate;
        @Nullable
        @BindView(R.id.iv_song)
        ImageView ivType;
        @Nullable
        @BindView(R.id.rv_ads)
        RelativeLayout rvAds;
        public ItemtView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
