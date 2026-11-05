package com.openapi.ks.myapp.adapter;

import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.core.ads.yewu.ads.base.AdsManager;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.bean.vo.ImgItemsVo;
import com.openapi.ks.myapp.bean.vo.TextItemsVo;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.HistoryUtil;

import java.util.List;

import com.openapi.ks.chat.databinding.LayoutChannelItemsItemBinding;

/**
 * Created by openapi on 2016/8/12.
 */
public class ImgChannelListItemsViewAdapter extends BaseRecyclerViewAdapter<ImgChannelListItemsViewAdapter.ImgChannleListView, ImgItemsVo> {

    public ImgChannelListItemsViewAdapter(Context context, RecyclerView recyclerView, List<ImgItemsVo> data, OnRecyclerViewItemClickListener<ImgItemsVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public ImgChannleListView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_channel_items_item, parent, false);
        return new ImgChannleListView(view, this, this);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        ImgChannleListView holder = (ImgChannleListView) h;
        ImgItemsVo vo = data.get(position);
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.fileDate);
        if (position == getSelected()) {
            holder.tvName.setTextColor(context.getResources().getColor(R.color.click));
        } else if (HistoryUtil.getHistory(context, vo.url) != null) {
            holder.tvName.setTextColor(context.getResources().getColor(R.color.base_gray));
        } else {
            holder.tvName.setTextColor(context.getResources().getColor(R.color.base_black));
        }
        if (Constants.BANNER_ADS_POS.contains(position)) {
            if (position % 2 == 1) {
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity) context, holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2);
            } else {
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity) context, holder.rvAds, AdsContext.Categrey.CATEGREY_VPN3);
            }
        } else {
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
        }
    }

    public static class ImgChannleListView extends BaseRecyclerViewHolder<TextItemsVo> {
        @Nullable
        TextView tvIndex;
        @Nullable
        TextView tvName;
        @Nullable
        TextView tvDate;
        @Nullable
        RelativeLayout rvAds;

        public ImgChannleListView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            LayoutChannelItemsItemBinding b = LayoutChannelItemsItemBinding.bind(itemView);
            tvIndex = b.tvIndex;
            tvName = b.tvName;
            tvDate = b.tvDate;
            rvAds = b.rvAds;
        }
    }
}
