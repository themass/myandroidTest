package com.qq.myapp.adapter;

import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kyview.natives.NativeAdInfo;
import com.qq.e.ads.nativ.NativeExpressADView;
import com.qq.kb.R;
import com.sspacee.common.util.CollectionUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.ads.base.GdtNativeManager;
import com.qq.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.qq.myapp.bean.vo.TextItemsVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.HistoryUtil;
import com.qq.myapp.data.ImagePhotoLoad;

import java.util.HashMap;
import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class NativeTextChannelListItemsViewAdapter extends BaseRecyclerViewAdapter<NativeTextChannelListItemsViewAdapter.TextChannleListView, TextItemsVo> {
    FragmentManager fragmentManager = ((FragmentActivity)context).getSupportFragmentManager();
    int currentContainerId = -1;
    GdtNativeManager gdtNativeManager;
    public NativeTextChannelListItemsViewAdapter(FragmentActivity context, RecyclerView recyclerView, List<TextItemsVo> data, OnRecyclerViewItemClickListener<TextItemsVo> listener, GdtNativeManager gdtNativeManager) {
        super(context, recyclerView, data, listener);
        this.gdtNativeManager = gdtNativeManager;
    }

    @Override
    public TextChannleListView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_channel_items_item, parent, false);
        return new TextChannleListView(view, this, this);
    }
    @Override
    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        TextChannleListView holder = (TextChannleListView)h;
        TextItemsVo vo = data.get(position);
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.fileDate);
        if (position == getSelected()) {
            holder.tvName.setTextColor(context.getResources().getColor(R.color.click));
        } else if (HistoryUtil.getHistory(context, vo.fileUrl) != null) {
            holder.tvName.setTextColor(context.getResources().getColor(R.color.base_gray));
        } else {
            holder.tvName.setTextColor(context.getResources().getColor(R.color.base_black));
        }
        if(gdtNativeManager!=null){
            if(!gdtNativeManager.showAds(position,holder.natvieView)){
                holder.natvieView.setVisibility(View.GONE);
            }
        }else{
            holder.natvieView.setVisibility(View.GONE);
        }
        if(Constants.BANNER_ADS_POS.contains(position)){
            if(position%3==0){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN3);
            }if(position%3==1){
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
    public int getUniqueId() {
        return (int) SystemClock.currentThreadTimeMillis();
    }
    public static class TextChannleListView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<TextItemsVo> {
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
        @BindView(R.id.rv_ads)
        RelativeLayout rvAds;
        @Nullable
        @BindView(R.id.natvieView)
        RelativeLayout natvieView;
        @Nullable
        @BindView(R.id.desc)
        TextView desc;
        @Nullable
        @BindView(R.id.icon)
        ImageView icon;
        public TextChannleListView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
