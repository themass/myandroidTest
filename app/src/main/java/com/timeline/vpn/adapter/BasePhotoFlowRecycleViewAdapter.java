package com.timeline.vpn.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.sspacee.common.util.Utils;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public abstract class BasePhotoFlowRecycleViewAdapter<M, T extends BasePhotoFlowRecycleViewAdapter.BaseViewHolder> extends RecyclerView.Adapter<T> {

    protected static final Interpolator INTERPOLATOR = new DecelerateInterpolator();
    private static final int PHOTO_ANIMATION_DELAY = 600;
    protected final Context context;
    protected final int cellSize;
    protected List<M> data = null;

    public BasePhotoFlowRecycleViewAdapter(Context context, RecyclerView recyclerView, List<M> data) {
        this.context = context;
        this.cellSize = Utils.getScreenWidth(context) / 2;
        if (data != null) {
            this.data = data;
        } else {
            this.data = new ArrayList<>();
            ;
        }
    }

    @Override
    public void onBindViewHolder(BasePhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position) {
        bindPhoto(holder, position);
    }

    private void bindPhoto(final BasePhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position) {
        long animationDelay = PHOTO_ANIMATION_DELAY + holder.getAdapterPosition() * 30;
        animatePhoto(holder, animationDelay, position);
    }

    abstract protected void animatePhoto(BaseViewHolder viewHolder, long animationDelay, int position);

    @Override
    public int getItemCount() {
        return data.size();
    }
    public static class BaseViewHolder extends RecyclerView.ViewHolder {
        public BaseViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
