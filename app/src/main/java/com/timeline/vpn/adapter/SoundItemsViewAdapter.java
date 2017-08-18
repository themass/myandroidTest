package com.timeline.vpn.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.SoundItemsVo;

import java.util.List;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/12.
 */
public class SoundItemsViewAdapter extends BaseRecyclerViewAdapter<SoundItemsViewAdapter.SoundItemView, SoundItemsVo> {

    public SoundItemsViewAdapter(Context context, RecyclerView recyclerView, List<SoundItemsVo> data, OnRecyclerViewItemClickListener<SoundItemsVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public SoundItemsViewAdapter.SoundItemView onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_sounditems_item, parent, false);
        return new SoundItemsViewAdapter.SoundItemView(view, this);
    }

    @Override
    public void onBindViewHolder(SoundItemsViewAdapter.SoundItemView holder, int position) {
        super.onBindViewHolder(holder, position);
        SoundItemsVo vo = data.get(position);
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.fileDate);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

     static class SoundItemView  extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<SoundItemsVo> {
        @Nullable
        @Bind(R.id.tv_index)
        TextView tvIndex;
        @Nullable
        @Bind(R.id.tv_name)
        TextView tvName;
        @Nullable
        @Bind(R.id.tv_date)
        TextView tvDate;
        public SoundItemView(View itemView, View.OnClickListener l) {
            super(itemView, l);
        }
    }
}
