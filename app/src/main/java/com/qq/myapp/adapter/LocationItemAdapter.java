package com.qq.myapp.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kyview.natives.NativeAdInfo;
import com.qq.common.util.LogUtil;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.ImagePhotoLoad;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.AdsManager;
import com.qq.common.util.CollectionUtils;
import com.qq.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.qq.myapp.bean.vo.LocationVo;
import com.qq.myapp.data.LocationUtil;
import com.qq.myapp.task.LocationPingTask;
import com.qq.fq2.R;
import com.qq.yewu.ads.base.GdtNativeManager;

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
    GdtNativeManager gdtNativeManager;

    public LocationItemAdapter(Context context, RecyclerView recyclerView, List<LocationVo> data, OnRecyclerViewItemClickListener<LocationVo> listener,GdtNativeManager gdtNativeManager) {
        super(context, recyclerView, data, listener);
        initSelectLocation();
        indexColo = context.getResources().getColorStateList(R.color.location_index);
        black = context.getResources().getColorStateList(R.color.base_black);
        indexSelectColo = context.getResources().getColorStateList(R.color.base_red);
        index = 0;
        this.gdtNativeManager =gdtNativeManager;
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
            holder.tvCountryEname.setTextColor(indexSelectColo);
        } else {
            holder.tvIndex.setTextColor(indexColo);
            holder.tvCountry.setTextColor(black);
            holder.tvCountryEname.setTextColor(black);
        }
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvCountry.setText(vo.name);
        holder.tvCountryEname.setText(vo.ename);

        if(Constants.BANNER_ADS_POS.contains(position)&&index==0){
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
        if(gdtNativeManager!=null&&index==1){
            if(!gdtNativeManager.showAds(position,holder.natvieView)){
                holder.natvieView.setVisibility(View.GONE);
            }else{
                holder.natvieView.setVisibility(View.VISIBLE);
            }
        }else{
            holder.natvieView.setVisibility(View.GONE);
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
