package com.timeline.vpn.ads.adview;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.kyview.natives.NativeAdInfo;
import com.timeline.vpn.R;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by gqli on 2016/8/19.
 */
public class NativeAdsAdapter {
    public static class AdsAdapter extends RecyclerView.Adapter<NativeAdsAdapter.AdsItemViewHolder> {
        private Context mContext;
        private List<NativeAdInfo> data = new ArrayList<>();

        public AdsAdapter(Context context) {
            this.mContext = context;
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public void addData(List<NativeAdInfo> info) {
            if (info != null) {
                data.clear();
                data.addAll(info);
                notifyDataSetChanged();
            }
        }

        public void removeData() {
            data.clear();
        }

        @Override
        public void onBindViewHolder(AdsItemViewHolder holder, final int position) {
            NativeAdInfo nativeAdInfo = (NativeAdInfo) data.get(position);
            holder.desc.setText(nativeAdInfo.getDescription());
            Glide.with(mContext)
                    .load(nativeAdInfo.getIconUrl())
                    .placeholder(R.drawable.vpn_trans_default)
                    .crossFade()
                    .into(holder.icon);
            holder.title.setText(nativeAdInfo.getTitle());
            Log.i("原生信息 ", "nativeAdInfo.descript: " + nativeAdInfo.getDescription() + "\nnativeAdInfo.icon: "
                    + nativeAdInfo.getIconUrl() + "\ndata.title:" + nativeAdInfo.getTitle());
            holder.rlItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NativeAdInfo info = (NativeAdInfo) data.get(position);
                    info.onClick(v);
                }
            });
        }

        @Override
        public NativeAdsAdapter.AdsItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(mContext).inflate(R.layout.tab_ads_native, parent, false);
            return new NativeAdsAdapter.AdsItemViewHolder(view);
        }
    }

    public static class AdsItemViewHolder extends RecyclerView.ViewHolder {
        @Bind(R.id.rl_item)
        RelativeLayout rlItem;
        @Bind(R.id.icon_layout)
        ImageView icon;
        @Bind(R.id.title)
        TextView title;
        @Bind(R.id.desc)
        TextView desc;
        @Bind(R.id.logo)
        TextView logo;

        public AdsItemViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
