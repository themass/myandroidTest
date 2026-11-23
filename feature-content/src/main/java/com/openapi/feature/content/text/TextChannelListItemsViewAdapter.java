package com.openapi.feature.content.text;

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

import com.openapi.common.ads.base.AdsContext;
import com.openapi.common.ads.base.AdsManager;
import com.openapi.feature.content.R;
import com.openapi.feature.content.databinding.LayoutChannelItemsItemBinding;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.feature.content.text.TextItemsVo;
import com.openapi.core.config.Constants;
import com.openapi.feature.content.common.HistoryUtil;

import java.util.List;

/**
 * Created by openapi on 2016/8/12.
 */
public class TextChannelListItemsViewAdapter extends BaseRecyclerViewAdapter<TextChannelListItemsViewAdapter.TextChannleListView, TextItemsVo> {
    FragmentManager fragmentManager = ((FragmentActivity) context).getSupportFragmentManager();
    int currentContainerId = -1;
    /**
     * 整改清单 P3-21：「看过置灰」以前在每次 bind 里同步查 Room（关掉
     * allowMainThreadQueries 后就是崩溃点），现在 bind 只查这份内存集合，
     * 列表由 {@link #setVisitedUrls} 后台装填、{@link #markVisited} 增量补。
     */
    private final com.openapi.feature.content.common.VisitedUrlCache visitedCache =
            new com.openapi.feature.content.common.VisitedUrlCache();

    public TextChannelListItemsViewAdapter(FragmentActivity context, RecyclerView recyclerView, List<TextItemsVo> data, OnRecyclerViewItemClickListener<TextItemsVo> listener) {
        super(context, recyclerView, data, listener);
    }

    /** 主线程回调里调用：装填已访问地址并重绘。 */
    public void setVisitedUrls(java.util.List<String> urls) {
        visitedCache.loadFrom(urls);
        notifyDataSetChanged();
    }

    /** 点开的地址立刻标已访问（addHistory 落库是异步的，不能等它）。 */
    public void markVisited(String url) {
        visitedCache.markVisited(url);
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
        } else if (visitedCache.isVisited(vo.fileUrl)) {
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
