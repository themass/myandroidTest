package com.openapi.ks.myapp.ui.user.setting;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.openapi.common.util.lang.StringUtils;
import com.openapi.core.ads.yewu.ads.base.AdsContext;
import com.openapi.core.ads.yewu.ads.base.AdsManager;
import com.openapi.feature.user.R;
import com.openapi.core.legacyui.adapter.BaseRecyclerViewAdapter;
import com.openapi.core.model.CharacterVo;
import com.openapi.core.config.Constants;
import com.openapi.ks.myapp.data.ImagePhotoLoad;

import java.util.List;

import com.openapi.feature.user.databinding.LayoutSettingCharacterItemBinding;

/**
 * Created by openapi on 2016/8/12.
 */
public class SettingCharacterAdapter extends BaseRecyclerViewAdapter<SettingCharacterAdapter.ItemtView, CharacterVo> {
    public SettingCharacterAdapter(Context context, RecyclerView recyclerView, List<CharacterVo> data, OnRecyclerViewItemClickListener<CharacterVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public ItemtView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_setting_character_item, parent, false);
        return new ItemtView(view, this, this);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
    }

    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        ItemtView holder = (ItemtView) h;
        CharacterVo vo = data.get(position);
        holder.tvName.setText(vo.title);
        holder.tvContent.setText(vo.content);
        if (!StringUtils.isEmpty(vo.getUrl())) {
            ImagePhotoLoad.loadCommonImg(context, vo.getUrl(), holder.ivBg);
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

    public static class ItemtView extends BaseRecyclerViewHolder<CharacterVo> {
        @Nullable
        ImageView ivBg;
        @Nullable
        TextView tvName;
        @Nullable
        TextView tvContent;
        @Nullable
        RelativeLayout rvAds;

        public ItemtView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            LayoutSettingCharacterItemBinding b = LayoutSettingCharacterItemBinding.bind(itemView);
            ivBg = b.ivBg;
            tvName = b.tvName;
            tvContent = b.tvContent;
            rvAds = b.rvAds;
        }
    }
}
