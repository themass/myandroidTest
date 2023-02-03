package com.ks.myapp.adapter;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.ks.sexfree1.R;
import com.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.ks.myapp.bean.vo.FavoriteVo;
import com.ks.myapp.constant.Constants;

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
        }else if (vo.type == Constants.FavoriteType.VIDEO) {
            holder.ivType.setImageResource(R.drawable.video);
        } else{
            holder.ivType.setVisibility(View.GONE);
        }
        if(Constants.BANNER_ADS_POS.contains(position)){
            if(position%2==1){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2);
            }else{
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN3);
            }
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