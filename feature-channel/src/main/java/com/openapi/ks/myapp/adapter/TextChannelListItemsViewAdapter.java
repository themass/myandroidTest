package com.openapi.ks.myapp.adapter;

import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.core.ads.yewu.ads.base.AdsManager;
import com.openapi.feature.channel.R;
import com.openapi.feature.channel.databinding.LayoutChannelItemsItemBinding;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.HistoryUtil;

import java.util.List;

/**
 * Created by openapi on 2016/8/12.
 */
public class TextChannelListItemsViewAdapter extends BaseRecyclerViewAdapter<TextChannelListItemsViewAdapter.TextChannleListView, TextItemsVo> {
    FragmentManager fragmentManager = ((FragmentActivity) context).getSupportFragmentManager();
    int currentContainerId = -1;

    public TextChannelListItemsViewAdapter(FragmentActivity context, RecyclerView recyclerView, List<TextItemsVo> data, OnRecyclerViewItemClickListener<TextItemsVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public TextChannleListView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_channel_items_item, parent, false);
        return new TextChannleListView(view, this, this);
    }

    @Override
    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        TextChannleListView holder = (TextChannleListView) h;
        TextItemsVo vo = data.get(position);
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.fileDate);
        if (position == getSelected()) {
            holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.click));
        } else if (HistoryUtil.getHistory(context, vo.fileUrl) != null) {
            holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.base_gray));
        } else {
            holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.base_black));
        }
        if (Constants.BANNER_ADS_POS.contains(position)) {
            if (position % 3 == 0) {
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity) context, holder.rvAds, AdsContext.Categrey.CATEGREY_VPN3);
            }
            if (position % 3 == 1) {
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity) context, holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2);
            } else {
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity) context, holder.rvAds, AdsContext.Categrey.CATEGREY_VPN1);
            }
        } else {
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
        }
    }

    public int getUniqueId() {
        return (int) SystemClock.currentThreadTimeMillis();
    }

    public static class TextChannleListView extends BaseRecyclerViewHolder<TextItemsVo> {
        @Nullable
        TextView tvIndex;
        @Nullable
        TextView tvName;
        @Nullable
        TextView tvDate;
        @Nullable
        RelativeLayout rvAds;

        public TextChannleListView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            LayoutChannelItemsItemBinding b = LayoutChannelItemsItemBinding.bind(itemView);
            tvIndex = b.tvIndex;
            tvName = b.tvName;
            tvDate = b.tvDate;
            rvAds = b.rvAds;
        }
    }
}
