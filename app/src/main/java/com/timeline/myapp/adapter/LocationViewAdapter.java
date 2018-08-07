package com.timeline.myapp.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.vpn.R;
import com.timeline.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.timeline.myapp.bean.vo.LocationVo;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.ImagePhotoLoad;
import com.timeline.myapp.data.LocationUtil;
import com.timeline.myapp.task.LocationPingTask;

import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class LocationViewAdapter extends BaseRecyclerViewAdapter<LocationViewAdapter.LocationItemView, LocationVo> {
    private int chooseId = 0;
    private ColorStateList indexColo = null;
    private ColorStateList indexSelectColo = null;
    private boolean needPing = false;
    private int index;
    public LocationViewAdapter(Context context, RecyclerView recyclerView, List<LocationVo> data, OnRecyclerViewItemClickListener<LocationVo> listener,int index) {
        super(context, recyclerView, data, listener);
        chooseId = LocationUtil.getSelectLocationId(context);
        indexColo = context.getResources().getColorStateList(R.color.location_index);
        indexSelectColo = context.getResources().getColorStateList(R.color.base_red);
        this.index = index;
    }
    @Override
    public LocationViewAdapter.LocationItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_location_choose_item, parent, false);
        return new LocationViewAdapter.LocationItemView(view, this, this);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        LocationViewAdapter.LocationItemView holder = (LocationViewAdapter.LocationItemView)h;
        LocationVo vo = data.get(position);

        if (chooseId == vo.id) {
            holder.tvIndex.setTextColor(indexSelectColo);
        } else {
            holder.tvIndex.setTextColor(indexColo);
        }
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvCountry.setText(vo.name);
        holder.tvCountryEname.setText(vo.ename);
        holder.rbStar.setRating(vo.level);
        //TODO:改成vip高级vip图标
        if (vo.type == Constants.LOCATION_TYPE_FREE) {
            holder.ivType.setBackgroundResource(R.drawable.bg_type_free);
            holder.tvType.setText(R.string.vpn_type_free);
        } else if (vo.type == Constants.LOCATION_TYPE_VIP) {
            holder.ivType.setBackgroundResource(R.drawable.bg_type_vip);
            holder.tvType.setText(R.string.vpn_type_vip);
        } else if (vo.type == Constants.LOCATION_TYPE_VIP2) {
            holder.ivType.setBackgroundResource(R.drawable.bg_type_advip);
            holder.tvType.setText(R.string.vpn_type_vip2);
        }else if (vo.type == Constants.LOCATION_TYPE_VIP3) {
            holder.ivType.setBackgroundResource(R.drawable.bg_type_paid);
            holder.tvType.setText(R.string.vpn_type_vip3);
        }else if (vo.type == Constants.LOCATION_TYPE_VIP4) {
            holder.ivType.setBackgroundResource(R.drawable.bg_type_vip4);
            holder.tvType.setText(R.string.vpn_type_vip4);
        }
        AdsContext.Categrey one = AdsContext.Categrey.CATEGREY_VPN;
        AdsContext.Categrey two = AdsContext.Categrey.CATEGREY_VPN1;
        if(index%2==1) {
            one = AdsContext.Categrey.CATEGREY_VPN2;
            two = AdsContext.Categrey.CATEGREY_VPN3;
        }
        if (position == getItemCount() - 1) {
            holder.rvAds.setVisibility(View.VISIBLE);
            AdsManager.getInstans().showBannerAds((FragmentActivity) context, holder.rvAds, one);
        } else if (position == 3 && AdsContext.rateShow()) {
            holder.rvAds.setVisibility(View.VISIBLE);
            AdsManager.getInstans().showBannerAds((FragmentActivity) context, holder.rvAds, two);
        } else {
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
        }
//        holder.tvPing.setTextColor(context.getResources().getColor(R.color.base_black));
        if(isNeedPing())
            LocationPingTask.ping(context,data.get(position),holder.pgPing,holder.tvPing);
        ImagePhotoLoad.getCountryImage(context, holder.ivCountry, vo.img);
    }

    public boolean isNeedPing() {
        return needPing;
    }

    public void setNeedPing(boolean needPing) {
        this.needPing = needPing;
        for(LocationVo vo:data){
            vo.ping=null;
        }
    }

    static class LocationItemView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<LocationVo> {
        @Nullable
        @BindView(R.id.ll_location_choose)
        LinearLayout llLocationChoose;
        @Nullable
        @BindView(R.id.loc_tv_index)
        TextView tvIndex;
        @Nullable
        @BindView(R.id.rl_local_type)
        RelativeLayout ivType;
        @Nullable
        @BindView(R.id.loc_tv_type)
        TextView tvType;
        @Nullable
        @BindView(R.id.loc_iv_country)
        ImageView ivCountry;
        @Nullable
        @BindView(R.id.loc_tv_country)
        TextView tvCountry;
        @Nullable
        @BindView(R.id.loc_tv_country_ename)
        TextView tvCountryEname;
        @Nullable
        @BindView(R.id.rb_start)
        RatingBar rbStar;
        @Nullable
        @BindView(R.id.tv_ping)
        TextView tvPing;
        @Nullable
        @BindView(R.id.pg_ping)
        ProgressBar pgPing;
        @Nullable
        @BindView(R.id.rv_ads)
        RelativeLayout rvAds;
        public LocationItemView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
