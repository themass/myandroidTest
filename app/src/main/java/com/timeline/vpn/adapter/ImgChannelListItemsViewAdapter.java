package com.timeline.vpn.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.ImgItemsVo;
import com.timeline.vpn.bean.vo.TextItemsVo;

import java.util.List;

import butterknife.BindView;

/**
 * Created by themass on 2016/8/12.
 */
public class ImgChannelListItemsViewAdapter extends BaseRecyclerViewAdapter<ImgChannelListItemsViewAdapter.ImgChannleListView, ImgItemsVo> {

    public ImgChannelListItemsViewAdapter(Context context, RecyclerView recyclerView, List<ImgItemsVo> data, OnRecyclerViewItemClickListener<ImgItemsVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public ImgChannleListView onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_channel_items_item, parent, false);
        return new ImgChannleListView(view, this, this);
    }

    @Override
    public void onBindViewHolder(ImgChannleListView holder, int position) {
        super.onBindViewHolder(holder, position);
        ImgItemsVo vo = data.get(position);
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.fileDate);
        if (position == getSelected()) {
            holder.tvName.setTextColor(context.getResources().getColor(R.color.click));
        } else {
            holder.tvName.setTextColor(context.getResources().getColor(R.color.base_black));
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public static class ImgChannleListView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<TextItemsVo> {
        @Nullable
        @BindView(R.id.tv_index)
        TextView tvIndex;
        @Nullable
        @BindView(R.id.tv_name)
        TextView tvName;
        @Nullable
        @BindView(R.id.tv_date)
        TextView tvDate;

        public ImgChannleListView(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView, l, longListener);
        }
    }
}
