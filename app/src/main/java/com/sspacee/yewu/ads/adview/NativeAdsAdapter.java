package com.sspacee.yewu.ads.adview;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kyview.natives.NativeAdInfo;
import com.timeline.vpn.R;
import com.timeline.vpn.base.MyApplication;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by themass on 2016/8/19.
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
            NativeAdInfo nativeAdInfo = data.get(position);
            holder.desc.setText(nativeAdInfo.getDescription());
            MyApplication.getInstance().getPhotoLoad().loadCommonImg(mContext, nativeAdInfo.getIconUrl(), holder.icon);
            holder.title.setText(nativeAdInfo.getTitle());
            Log.i("原生信息 ", "nativeAdInfo.descript: " + nativeAdInfo.getDescription() + "\nnativeAdInfo.icon: "
                    + nativeAdInfo.getIconUrl() + "\ndata.title:" + nativeAdInfo.getTitle());
            holder.rlItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NativeAdInfo info = data.get(position);
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
        @BindView(R.id.rl_item)
        RelativeLayout rlItem;
        @BindView(R.id.icon_layout)
        ImageView icon;
        @BindView(R.id.title)
        TextView title;
        @BindView(R.id.desc)
        TextView desc;
        @BindView(R.id.logo)
        TextView logo;

        public AdsItemViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
