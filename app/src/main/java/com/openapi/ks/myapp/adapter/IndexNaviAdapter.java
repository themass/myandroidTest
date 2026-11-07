package com.openapi.ks.myapp.adapter;

import android.content.Context;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.openapi.core.util.DisplayUtil;
import com.openapi.core.util.Utils;
import com.openapi.ks.myapp.base.MyApplication;
import com.openapi.ks.chat.R;
import com.openapi.ks.chat.databinding.TabIndexItemRecommendBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexNaviAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int PHOTO_ANIMATION_DELAY = 600;
    private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();
    private static final Random a = new Random();
    private final Context context;
    private final int cellSize;
    private final List<String> photos;
    protected boolean isScrolling;
    private boolean lockedAnimations = false;
    private int lastAnimatedItem = -1;
    private RecyclerView mRecyclerView;
    private SparseIntArray mPostCache = new SparseIntArray();

    public IndexNaviAdapter(Context context, RecyclerView recyclerView, List<String> photos) {
        this.context = context;
        this.cellSize = Utils.getScreenWidth(context) / 2;
        if (photos == null) {
            this.photos = new ArrayList<>();
        } else {
            this.photos = photos;
        }
        this.mRecyclerView = recyclerView;
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                isScrolling = !(newState == RecyclerView.SCROLL_STATE_IDLE);
                if (!isScrolling) {
                    notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(context).inflate(R.layout.tab_index_item_recommend, parent, false);
        return new NaviItemViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        Integer high = mPostCache.get(position);
        if (high == 0) {
            high = cellSize + DisplayUtil.dp2px(context, 10 * (a.nextInt(9) / 3 * 3));
            mPostCache.put(position, high);
        }
        return high;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        StaggeredGridLayoutManager.LayoutParams layoutParams = (StaggeredGridLayoutManager.LayoutParams) holder.itemView.getLayoutParams();
        layoutParams.setFullSpan(false);
        layoutParams.height = mPostCache.get(position);
        layoutParams.width = cellSize;
        holder.itemView.setLayoutParams(layoutParams);
        BindViewPhoto((NaviItemViewHolder) holder, position);

    }

    private void BindViewPhoto(final NaviItemViewHolder holder, int position) {
        MyApplication.getInstance().getPhotoLoad().loadCommonImg(context, photos.get(position), holder.ivPhoto);
        animatePhoto(holder);
        if (lastAnimatedItem < position) lastAnimatedItem = position;
    }

    private void animatePhoto(NaviItemViewHolder viewHolder) {
        if (!lockedAnimations) {
            if (lastAnimatedItem == viewHolder.getPosition()) {
                setLockedAnimations(true);
            }
            long animationDelay = PHOTO_ANIMATION_DELAY + viewHolder.getPosition() * 30;
            viewHolder.cardView.setScaleY(0);
            viewHolder.cardView.setScaleX(0);
            viewHolder.cardView.animate()
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
        CardView cardView;
        TextView ivTitle;
        ImageView ivPhoto;

        public NaviItemViewHolder(View view) {
            super(view);
            TabIndexItemRecommendBinding b = TabIndexItemRecommendBinding.bind(view);
            cardView = b.cardView;
            ivTitle = b.tvTitle;
            ivPhoto = b.ivPhoto;
        }
    }
}
