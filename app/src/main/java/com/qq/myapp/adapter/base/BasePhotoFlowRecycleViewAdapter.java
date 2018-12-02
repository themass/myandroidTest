package com.qq.myapp.adapter.base;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.DragEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.qq.common.helper.ItemTouchHelperAdapter;
import com.qq.common.helper.OnStartDragListener;
import com.qq.common.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.ButterKnife;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public abstract class BasePhotoFlowRecycleViewAdapter<M, T extends BasePhotoFlowRecycleViewAdapter.BaseViewHolder> extends RecyclerView.Adapter<T> implements ItemTouchHelperAdapter {

    protected static final Interpolator INTERPOLATOR = new DecelerateInterpolator();
    private static final int PHOTO_ANIMATION_DELAY = 600;
    protected final Context context;
    protected final int cellSize;
    protected List<M> data = null;
    private OnStartDragListener mDragStartListener;
    private OnRecyclerViewItemClickListener mOnItemClickListener;

    public BasePhotoFlowRecycleViewAdapter(Context context, RecyclerView recyclerView, List<M> data, OnRecyclerViewItemClickListener onItemClickListener, OnStartDragListener dragStartListener) {
        this.context = context;
        this.mDragStartListener = dragStartListener;
        this.mOnItemClickListener = onItemClickListener;
        this.cellSize = Utils.getScreenWidth(context) / 2;
        if (data != null) {
            this.data = data;
        } else {
            this.data = new ArrayList<>();
        }
    }

    @Override
    public void onBindViewHolder(final BasePhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position) {
        bindPhoto(holder, position);
        if (mDragStartListener != null) {
            holder.itemView.setOnDragListener(new View.OnDragListener() {
                @Override
                public boolean onDrag(View v, DragEvent event) {
                    mDragStartListener.onStartDrag(holder);
                    return true;
                }
            });
        }
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

    private void bindPhoto(final BasePhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position) {
        long animationDelay = PHOTO_ANIMATION_DELAY + holder.getAdapterPosition() * 30;
        animatePhoto(holder, animationDelay, position);
    }

    abstract protected void animatePhoto(BaseViewHolder viewHolder, long animationDelay, int position);

    abstract protected void onCustomeBindViewHolder(final BasePhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position);

    protected boolean onLongFlag(final BaseViewHolder holder, boolean flag) {
        return false;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public void onItemDismiss(int position) {
        Object o = data.remove(position);
        notifyItemRemoved(position);
        mDragStartListener.onItemRemove(o);
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        Collections.swap(data, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        mDragStartListener.onItemMove(data.get(fromPosition), data.get(toPosition));
        return true;
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
