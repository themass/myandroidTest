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
import com.openapi.myapp.ui.fragment.LocationItemFragment;
import com.openapi.ks.free1.R;
import com.openapi.yewu.ads.config.BannerAdsNext;

import java.util.List;

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

    /** 构造时读取一次：列表存活期间样式不变，切换后重新进入页面生效。 */
    private final boolean cardStyle;

    public LocationItemAdapter(Context context, RecyclerView recyclerView, List<LocationVo> data, OnRecyclerViewItemClickListener<LocationVo> listener) {
        super(context, recyclerView, data, listener);
        cardStyle = com.openapi.common.util.PreferenceUtils.getPrefBoolean(
                context, Constants.LIST_CARD_STYLE, true);
        initSelectLocation();
        indexColo = context.getResources().getColorStateList(R.color.location_index);
        black = context.getResources().getColorStateList(R.color.pv_text_primary);
        indexSelectColo = context.getResources().getColorStateList(R.color.pv_green);
        index = 0;
        LogUtil.i(index+"");
    }
    @Override
    public LocationItemAdapter.LocationItemView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(
                cardStyle ? R.layout.layout_location_item_card : R.layout.layout_location_item_fragment,
                parent, false);
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
        holder.tvCountryCategory.setText(vo.category);
        if (cardStyle) {
            // 卡片式用背景表达"当前选中"，名称保持黑色，避免红字叠蓝框显得杂乱。
            holder.tvCountry.setTextColor(black);
            holder.llLocationChoose.setBackgroundResource(chooseId == vo.id
                    ? R.drawable.bg_location_card_selected : R.drawable.bg_location_card);
            // 与服务端 checkPermission 一致：type 0 和 4 是免费，1~3 才是 VIP 线路。
            // Best 占位行不是真实线路，不标 VIP。
            boolean isBest = vo.id == LocationItemFragment.BEST_ID;
            holder.tvVip.setVisibility(!isBest && vo.type >= 1 && vo.type <= 3 ? View.VISIBLE : View.GONE);
        }


//        if(Constants.BANNER_ADS_POS.contains(position)&&index==0){
//            if(position%2==1){
//                holder.rvAds.setVisibility(View.VISIBLE);
//                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN1,AdsContext.AdsFrom.ADMOB);
//            }
////            else{
////                holder.rvAds.setVisibility(View.VISIBLE);
////                AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN2,AdsContext.AdsFrom.MOBVISTA);
////            }
//        } else if((position==data.size()-1)&&index==1){
//            holder.rvAds.setVisibility(View.VISIBLE);
//            AdsManager.getInstans().showBannerAds((FragmentActivity)context,holder.rvAds, AdsContext.Categrey.CATEGREY_VPN);
//        } else{
            holder.rvAds.removeAllViews();
            holder.rvAds.setVisibility(View.GONE);
//        }
        holder.natvieView.setVisibility(View.GONE);

        LocationPingTask.fillText(context,holder.pgPing,holder.tvPing,vo.ping);
//        holder.tvPing.setTextColor(context.getResources().getColor(R.color.base_black));
        if(isNeedPing())
            LocationPingTask.ping(context,data.get(position),holder.pgPing,holder.tvPing);
        if (vo.id == LocationItemFragment.BEST_ID) {
            // 占位行没有国旗。必须显式设置图标，否则 RecyclerView 复用的 ImageView 会残留上一个节点的国旗。
            holder.ivCountry.setImageResource(android.R.drawable.ic_menu_compass);
        } else {
            ImagePhotoLoad.getCountryImage(context, holder.ivCountry, vo.img);
        }
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
        LinearLayout llLocationChoose;
        @Nullable
        TextView tvIndex;
        @Nullable
        ImageView ivCountry;
        @Nullable
        TextView tvCountry;
        @Nullable
        TextView tvCountryCategory;
        /** 仅卡片布局有；原行式布局里为 null。 */
        @Nullable
        TextView tvVip;
        @Nullable
        TextView tvPing;
        @Nullable
        ProgressBar pgPing;
        @Nullable
        RelativeLayout rvAds;
        @Nullable
        RelativeLayout natvieView;
        @Nullable
        TextView desc;
        @Nullable
        ImageView icon;
        public LocationItemView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            llLocationChoose = itemView.findViewById(R.id.ll_location_choose);
            tvIndex = itemView.findViewById(R.id.loc_tv_index);
            ivCountry = itemView.findViewById(R.id.loc_iv_country);
            tvCountry = itemView.findViewById(R.id.loc_tv_country);
            tvCountryCategory = itemView.findViewById(R.id.loc_tv_country_category);
            tvVip = itemView.findViewById(R.id.loc_tv_vip);
            tvPing = itemView.findViewById(R.id.tv_ping);
            pgPing = itemView.findViewById(R.id.pg_ping);
            rvAds = itemView.findViewById(R.id.rv_ads);
            natvieView = itemView.findViewById(R.id.natvieView);
            desc = itemView.findViewById(R.id.desc);
            icon = itemView.findViewById(R.id.icon);
        }
    }
}
