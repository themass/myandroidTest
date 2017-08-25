package com.timeline.vpn.adapter;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.TextItemsVo;

import java.util.List;

import butterknife.Bind;

/**
 * Created by themass on 2016/8/12.
 */
public class ChannelListItemsViewAdapter extends BaseRecyclerViewAdapter<ChannelListItemsViewAdapter.TextChannleListView, TextItemsVo> {
    private int selected=-1;
    public ChannelListItemsViewAdapter(Context context, RecyclerView recyclerView, List<TextItemsVo> data, OnRecyclerViewItemClickListener<TextItemsVo> listener) {
        super(context, recyclerView, data, listener);
    }

    @Override
    public TextChannleListView onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_textlist_item, parent, false);
        return new TextChannleListView(view, this);
    }
    @Override
    public void onClick(View v) {
       super.onClick(v);
        selected = (int) v.getTag(R.id.tag_pos);
    }
    @Override
    public void onBindViewHolder(TextChannleListView holder, int position) {
        super.onBindViewHolder(holder, position);
        TextItemsVo vo = data.get(position);
        holder.tvIndex.setText("#" + (position + 1));
        holder.tvName.setText(vo.name);
        holder.tvDate.setText(vo.fileDate);
        if(position == selected){
            holder.tvName.setTextColor(context.getResources().getColor(R.color.click));
        }else{
            holder.tvName.setTextColor(context.getResources().getColor(R.color.base_black));
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

     public static class TextChannleListView extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder<TextItemsVo> {
         @Nullable
         @Bind(R.id.tv_index)
         TextView tvIndex;
         @Nullable
         @Bind(R.id.tv_name)
         TextView tvName;
         @Nullable
         @Bind(R.id.tv_date)
         TextView tvDate;
        public TextChannleListView(View itemView, View.OnClickListener l) {
            super(itemView, l);
        }
    }
}
