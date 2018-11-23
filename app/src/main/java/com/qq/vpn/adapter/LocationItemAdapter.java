package com.qq.vpn.adapter;

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
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.qq.ads.base.AdsContext;
import com.qq.ads.base.AdsManager;
import com.qq.ext.util.CollectionUtils;
import com.qq.network.R;
import com.qq.vpn.adapter.base.BaseRecyclerViewAdapter;
import com.qq.vpn.domain.res.LocationVo;
import com.qq.vpn.support.ImagePhotoLoad;
import com.qq.vpn.support.LocationUtil;
import com.qq.vpn.support.task.LocationPingTask;

import java.util.List;

import butterknife.BindView;

/**
 * Created by dengt on 2016/8/12.
 */
public class LocationItemAdapter extends BaseRecyclerViewAdapter<LocationItemAdapter.LocationItemView, LocationVo> {
    private int chooseId = 0;
    private ColorStateList indexColo = null;
    private ColorStateList indexSelectColo = null;
    private boolean needPing = false;
    private int index;
    public LocationItemAdapter(Context context, RecyclerView recyclerView, List<LocationVo> data, OnRecyclerViewItemClickListener<LocationVo> listener) {
        super(context, recyclerView, data, listener);
        chooseId = LocationUtil.getSelectLocationId(context);
        indexColo = context.getResources().getColorStateList(R.color.location_index);
        indexSelectColo = context.getResources().getColorStateList(R.color.base_red);
        index = 0;
        if(!CollectionUtils.isEmpty(data)){
            index = data.get(0).type;
        }
    }
    @Override
    public LocationItemAdapter.LocationItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_location_item_fragment, parent, false);
        return new LocationItemAdapter.LocationItemView(view, this, this);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        LocationItemAdapter.LocationItemView holder = (LocationItemAdapter.LocationItemView)h;
        LocationVo vo = data.get(position);

        if (chooseId == vo.id) {
            holder.tvIndex.setTextColor(indexSelectColo);
        } else {
            holder.tvIndex.setTextColor(indexColo);
        }
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvCountry.setText(vo.name);
        holder.tvCountryEname.setText(vo.ename);

        AdsContext.Categrey one = AdsContext.Categrey.CATEGREY_VPN;
//        AdsContext.Categrey two = AdsContext.Categrey.CATEGREY_VPN1;
//        if(index%2==1) {
//            one = AdsContext.Categrey.CATEGREY_VPN1;
////            two = AdsContext.Categrey.CATEGREY_VPN1;
//        }
        if (position == 1) {
            holder.rvAds.setVisibility(View.VISIBLE);
            AdsManager.getInstans().showBannerAds((FragmentActivity) context, holder.rvAds, one);
        }  else {
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
        }
        LocationPingTask.fillText(context,holder.pgPing,holder.tvPing,vo.ping);
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
        @BindView(R.id.loc_iv_country)
        ImageView ivCountry;
        @Nullable
        @BindView(R.id.loc_tv_country)
        TextView tvCountry;
        @Nullable
        @BindView(R.id.loc_tv_country_ename)
        TextView tvCountryEname;
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
