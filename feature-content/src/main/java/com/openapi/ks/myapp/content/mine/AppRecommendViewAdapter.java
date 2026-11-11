package com.openapi.ks.myapp.content.mine;

import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.openapi.feature.content.R;
import com.openapi.ks.myapp.adapter.base.BaseRecyclerViewAdapter;
import com.openapi.ks.myapp.content.mine.AppInfo;
import com.openapi.ks.myapp.data.ImagePhotoLoad;

import java.util.List;

import com.openapi.feature.content.databinding.LayoutAppItemBinding;

/**
 * Created by openapi on 2016/8/12.
 */
public class AppRecommendViewAdapter extends BaseRecyclerViewAdapter<AppRecommendViewAdapter.AppRecommendListView, AppInfo> {
    public AppRecommendViewAdapter(FragmentActivity context, RecyclerView recyclerView, List<AppInfo> data, OnRecyclerViewItemClickListener<AppInfo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public AppRecommendListView onCreateViewHolderData(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_app_item, parent, false);
        return new AppRecommendListView(view, this, this);
    }

    @Override
    public void onBindViewHolderData(RecyclerView.ViewHolder h, int position) {
        AppRecommendListView holder = (AppRecommendListView) h;
        AppInfo vo = data.get(position);
        ImagePhotoLoad.loadCommonImg(context, vo.img, holder.ivApp);
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.date);
        holder.tvDesc.setText(vo.desc);
    }

    public int getUniqueId() {
        return (int) SystemClock.currentThreadTimeMillis();
    }

    public static class AppRecommendListView extends BaseRecyclerViewHolder<AppInfo> {
        @Nullable
        ImageView ivApp;
        @Nullable
        TextView tvName;
        @Nullable
        TextView tvDate;
        @Nullable
        TextView tvDesc;

        public AppRecommendListView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
            LayoutAppItemBinding b = LayoutAppItemBinding.bind(itemView);
            ivApp = b.ivApp;
            tvName = b.tvName;
            tvDate = b.tvDate;
            tvDesc = b.tvDesc;
        }
    }
}
