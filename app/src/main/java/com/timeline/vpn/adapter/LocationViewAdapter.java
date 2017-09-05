package com.timeline.vpn.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.LocationVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.ImagePhotoLoad;
import com.timeline.vpn.data.LocationUtil;

import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class LocationViewAdapter extends BaseRecyclerViewAdapter<LocationViewAdapter.LocationItemView, LocationVo> {
    private int chooseId = 0;
    private ColorStateList indexColo = null;
    private ColorStateList indexSelectColo = null;

    public LocationViewAdapter(Context context, RecyclerView recyclerView, List<LocationVo> data, OnRecyclerViewItemClickListener<LocationVo> listener) {
        super(context, recyclerView, data, listener);
        chooseId = LocationUtil.getSelectId(context);
        indexColo = context.getResources().getColorStateList(R.color.location_index);
        indexSelectColo = context.getResources().getColorStateList(R.color.base_red);
    }

    @Override
    public LocationViewAdapter.LocationItemView onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_location_choose_item, parent, false);
        return new LocationViewAdapter.LocationItemView(view, this, this);
    }

    @Override
    public void onBindViewHolder(LocationViewAdapter.LocationItemView holder, int position) {
        super.onBindViewHolder(holder, position);
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
            holder.ivType.setImageResource(R.drawable.bg_type_free);
            holder.tvType.setText(R.string.vpn_type_free);
        } else if (vo.type == Constants.LOCATION_TYPE_VIP) {
            holder.ivType.setImageResource(R.drawable.bg_type_vip);
            holder.tvType.setText(R.string.vpn_type_vip);
        } else {
            holder.ivType.setImageResource(R.drawable.bg_type_advip);
            holder.tvType.setText(R.string.vpn_type_advip);
        }
        ImagePhotoLoad.getCountryImage(context, holder.ivCountry, vo.img);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class LocationItemView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<LocationVo> {
        @Nullable
        @BindView(R.id.ll_location_choose)
        LinearLayout llLocationChoose;
        @Nullable
        @BindView(R.id.loc_tv_index)
        TextView tvIndex;
        @Nullable
        @BindView(R.id.loc_iv_type)
        ImageView ivType;
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

        public LocationItemView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
