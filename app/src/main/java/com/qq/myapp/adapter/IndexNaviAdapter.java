package com.qq.myapp.adapter;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.qq.common.util.DisplayUtil;
import com.qq.common.util.Utils;
import com.qq.myapp.base.MyApplication;
import com.qq.ks.free1.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import butterknife.BindView;
import butterknife.ButterKnife;

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
        @BindView(R.id.card_view)
        CardView cardView;
        @BindView(R.id.tv_title)
        TextView ivTitle;
        @BindView(R.id.iv_photo)
        ImageView ivPhoto;

        public NaviItemViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
