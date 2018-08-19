package com.qq.vpn.adapter.base;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;


import com.qq.ext.util.Utils;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;

/**
 * Created by dengt on 14-03-12.
 */
public abstract class PhotoFlowRecycleViewAdapter<M, T extends PhotoFlowRecycleViewAdapter.BaseViewHolder> extends RecyclerView.Adapter<T> {

    protected static final Interpolator INTERPOLATOR = new DecelerateInterpolator();
    private static final int PHOTO_ANIMATION_DELAY = 600;
    protected final Context context;
    protected final int cellSize;
    protected List<M> data = null;
    private OnRecyclerViewItemClickListener mOnItemClickListener;

    public PhotoFlowRecycleViewAdapter(Context context, RecyclerView recyclerView, List<M> data, OnRecyclerViewItemClickListener onItemClickListener) {
        this.context = context;
        this.mOnItemClickListener = onItemClickListener;
        this.cellSize = Utils.getScreenWidth(context) / 2;
        if (data != null) {
            this.data = data;
        } else {
            this.data = new ArrayList<>();
        }
    }

    @Override
    public void onBindViewHolder(final PhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position) {
        bindPhoto(holder, position);
        holder.itemView.setTag(position);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnItemClickListener != null) {
                    mOnItemClickListener.onItemClick(v, (int) v.getTag());
                }
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mOnItemClickListener != null) {
                    mOnItemClickListener.onLongItemClick(v, (int) v.getTag());
                }
                return true;
            }
        });
        onCustomeBindViewHolder(holder, position);
    }

    private void bindPhoto(final PhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position) {
        long animationDelay = PHOTO_ANIMATION_DELAY + holder.getAdapterPosition() * 30;
        animatePhoto(holder, animationDelay, position);
    }

    abstract protected void animatePhoto(BaseViewHolder viewHolder, long animationDelay, int position);

    abstract protected void onCustomeBindViewHolder(final PhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position);

    protected boolean onLongFlag(final BaseViewHolder holder, boolean flag) {
        return false;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public interface OnRecyclerViewItemClickListener<T> {
        void onLongItemClick(View view, int position);

        void onItemClick(View view, int position);
    }

    public static class BaseViewHolder extends RecyclerView.ViewHolder {
        public BaseViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }

}
