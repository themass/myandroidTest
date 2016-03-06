package com.timeline.vpn.ui.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.timeline.vpn.R;
import com.timeline.vpn.common.util.Utils;

import java.util.Arrays;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexNaviAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int PHOTO_ANIMATION_DELAY = 600;
    private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();
    private final Context context;
    private final int cellSize;
    private final List<String> photos;
    private boolean lockedAnimations = false;
    private int lastAnimatedItem = -1;

    public IndexNaviAdapter(Context context) {
        this.context = context;
        this.cellSize = Utils.getScreenWidth(context) / 3;
        this.photos = Arrays.asList(context.getResources().getStringArray(R.array.user_photos));
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(context).inflate(R.layout.item_navi, parent, false);
        StaggeredGridLayoutManager.LayoutParams layoutParams = (StaggeredGridLayoutManager.LayoutParams) view.getLayoutParams();
        layoutParams.height = cellSize;
        layoutParams.width = cellSize;
        layoutParams.setFullSpan(false);
        view.setLayoutParams(layoutParams);
        return new NaviItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        bindPhoto((NaviItemViewHolder) holder, position);
    }

    private void bindPhoto(final NaviItemViewHolder holder, int position) {
        Glide.with(context)
                .load(photos.get(position))
                .centerCrop()
                .placeholder(R.drawable.ic_vpn_on)
                .crossFade()
                .into(holder.ivPhoto);
//        animatePhoto(holder);
//        if (lastAnimatedItem < position) lastAnimatedItem = position;
    }

    private void animatePhoto(NaviItemViewHolder viewHolder) {
        if (!lockedAnimations) {
            if (lastAnimatedItem == viewHolder.getPosition()) {
                setLockedAnimations(true);
            }
            long animationDelay = PHOTO_ANIMATION_DELAY + viewHolder.getPosition() * 30;
            viewHolder.flRoot.setScaleY(0);
            viewHolder.flRoot.setScaleX(0);
            viewHolder.flRoot.animate()
                    .scaleY(1)
                    .scaleX(1)
                    .setDuration(200)
                    .setInterpolator(INTERPOLATOR)
                    .setStartDelay(animationDelay)
                    .start();
        }
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    public void setLockedAnimations(boolean lockedAnimations) {
        this.lockedAnimations = lockedAnimations;
    }

    static class NaviItemViewHolder extends RecyclerView.ViewHolder {
        @Bind(R.id.flRoot)
        FrameLayout flRoot;
        @Bind(R.id.ivPhoto)
        ImageView ivPhoto;

        public NaviItemViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
