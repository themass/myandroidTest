package com.timeline.vpn.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.timeline.vpn.R;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;

/**
 * Created by themass on 2016/8/13.
 */
public abstract class BaseRecyclerViewAdapter<T extends BaseRecyclerViewAdapter.BaseRecyclerViewHolder, V> extends RecyclerView.Adapter<T> implements View.OnClickListener, View.OnLongClickListener {
    protected final Context context;
    protected RecyclerView mRecyclerView;
    protected OnRecyclerViewItemClickListener<V> listener;
    protected OnRecyclerViewItemLongClickListener<V> longClickListener;
    protected List<V> data;
    private int selected = -1;

    public BaseRecyclerViewAdapter(Context context, RecyclerView recyclerView, List<V> data, OnRecyclerViewItemClickListener<V> listener) {
        this.context = context;
        this.mRecyclerView = recyclerView;
        this.listener = listener;
        if (data != null) {
            this.data = data;
        } else {
            this.data = new ArrayList<>();
        }
    }

    @Override
    public void onBindViewHolder(T holder, int position) {
        holder.setData(position, data.get(position));
    }

    public void setLongClickListener(OnRecyclerViewItemLongClickListener<V> listener) {
        longClickListener = listener;
    }

    @Override
    public void onClick(View v) {
        selected = (int) v.getTag(R.id.tag_pos);
        if (listener != null)
            listener.onItemClick(v, (V) v.getTag(R.id.tag_val), (int) v.getTag(R.id.tag_pos));
    }

    @Override
    public boolean onLongClick(View v) {
        if (longClickListener != null) {
            longClickListener.onItemLongClick(v, (V) v.getTag(R.id.tag_val), (int) v.getTag(R.id.tag_pos));
            return true;
        }
        return false;
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }

    public interface OnRecyclerViewItemClickListener<T> {
        void onItemClick(View view, T data, int position);
    }

    public interface OnRecyclerViewItemLongClickListener<T> {
        void onItemLongClick(View view, T data, int position);
    }

    public static abstract class BaseRecyclerViewHolder<V> extends RecyclerView.ViewHolder {
        private View v;

        public BaseRecyclerViewHolder(View itemView, View.OnClickListener l, View.OnLongClickListener longListener) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            v = itemView;
            v.setOnClickListener(l);
            if (longListener != null)
                v.setOnLongClickListener(longListener);
        }

        public void setData(int pos, V val) {
            v.setTag(R.id.tag_pos, pos);
            v.setTag(R.id.tag_val, val);
        }
    }
}

