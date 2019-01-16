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

import com.qq.sexfree.R;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;

import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.SoundItemsVo;
import com.timeline.myapp.constant.Constants;

import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class SoundItemsViewAdapter extends BaseRecyclerViewAdapter<SoundItemsViewAdapter.SoundItemView, SoundItemsVo> {
    public SoundItemsViewAdapter(Context context, RecyclerView recyclerView, List<SoundItemsVo> data, OnRecyclerViewItemClickListener<SoundItemsVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public SoundItemsViewAdapter.SoundItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_channel_items_item, parent, false);
        return new SoundItemsViewAdapter.SoundItemView(view, this, this);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        SoundItemsViewAdapter.SoundItemView holder = (SoundItemsViewAdapter.SoundItemView)h;
        SoundItemsVo vo = data.get(position);
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.fileDate);
        if (position == getSelected()) {
            holder.ivSong.setVisibility(View.VISIBLE);
        } else {
            holder.ivSong.setVisibility(View.GONE);
        }
        if(Constants.BANNER_ADS_POS.contains(position)){
            if(position%2==1){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2);
            }else{
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN1);
            }
        }else{
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
        }
    }
    static class SoundItemView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<SoundItemsVo> {
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
        ImageView ivSong;
        @Nullable
        @BindView(R.id.rv_ads)
        RelativeLayout rvAds;
        public SoundItemView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
