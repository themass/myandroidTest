package com.openapi.myapp.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openapi.common.util.LogUtil;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.ImagePhotoLoad;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.AdsManager;
import com.openapi.common.util.CollectionUtils;
import com.openapi.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.myapp.bean.vo.LocationVo;
import com.openapi.myapp.data.LocationUtil;
import com.openapi.myapp.task.LocationPingTask;
import com.openapi.ks.free1.R;

import java.util.List;

import butterknife.BindView;

/**
 * Created by dengt on 2016/8/12.
 */
public class LocationItemAdapter extends BaseRecyclerViewAdapter<LocationItemAdapter.LocationItemView, LocationVo> {
    private int chooseId = 0;
    private ColorStateList indexColo = null;
    private ColorStateList indexSelectColo = null;
    private ColorStateList black = null;
    private boolean needPing = false;
    private int index;

    public LocationItemAdapter(Context context, RecyclerView recyclerView, List<LocationVo> data, OnRecyclerViewItemClickListener<LocationVo> listener) {
        super(context, recyclerView, data, listener);
        initSelectLocation();
        indexColo = context.getResources().getColorStateList(R.color.location_index);
        black = context.getResources().getColorStateList(R.color.base_black);
        indexSelectColo = context.getResources().getColorStateList(R.color.base_red);
        index = 0;
        LogUtil.i(index+"");
    }
    @Override
    public LocationItemAdapter.LocationItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_location_item_fragment, parent, false);
        return new LocationItemAdapter.LocationItemView(view, this, this);
    }
    public void initSelectLocation(){
        chooseId = LocationUtil.getSelectLocationId(context);
    }
    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        LocationItemAdapter.LocationItemView holder = (LocationItemAdapter.LocationItemView)h;
        LocationVo vo = data.get(position);
        if(!CollectionUtils.isEmpty(data)){
            index = data.get(0).type;
        }
        if (chooseId == vo.id) {
            holder.tvIndex.setTextColor(indexSelectColo);
            holder.tvCountry.setTextColor(indexSelectColo);
        } else {
            holder.tvIndex.setTextColor(indexColo);
            holder.tvCountry.setTextColor(black);
        }
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvCountry.setText(vo.ename);


        if(Constants.BANNER_ADS_POS.contains(position)&&index==0){
            if(position%2==1){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2);
            }else{
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN3);
            }
        }else if((position==data.size()-1)&&index==0){
            holder.rvAds.setVisibility(View.VISIBLE);
            AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2, AdsContext.AdsFrom.MOBVISTA);
        }else  if(Constants.BANNER_ADS_POS1.contains(position)&&index==1){
            if(position%2==1){
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN,AdsContext.AdsFrom.MOBVISTA);
            }else{
                holder.rvAds.setVisibility(View.VISIBLE);
                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN3,AdsContext.AdsFrom.MOBVISTA);
            }
        }else if((position==data.size()-1)&&index==1){
            holder.rvAds.setVisibility(View.VISIBLE);
            AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN, AdsContext.AdsFrom.MOBVISTA);
        } else{
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
        }
        holder.natvieView.setVisibility(View.GONE);

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
        @BindView(R.id.tv_ping)
        TextView tvPing;
        @Nullable
        @BindView(R.id.pg_ping)
        ProgressBar pgPing;
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
        public LocationItemView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
