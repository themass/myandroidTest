package com.timeline.vpn.ui.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.SparseArray;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.timeline.vpn.common.util.DisplayUtil;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import butterknife.ButterKnife;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public abstract class BasePhotoFlowRecycleViewAdapter<M,T extends BasePhotoFlowRecycleViewAdapter.BaseViewHolder> extends RecyclerView.Adapter<T> {

    private static final int PHOTO_ANIMATION_DELAY = 600;
    protected static final Interpolator INTERPOLATOR = new DecelerateInterpolator();
    protected final Context context;
    private final int cellSize;
    protected List<M> data = null;
    private boolean lockedAnimations = false;
    private int lastAnimatedItem = -1;
    protected boolean isScrolling;
    private static  final Random a = new Random();
    private RecyclerView mRecyclerView;
    private SparseArray<Integer> mPostCache = new SparseArray<Integer>();

    public BasePhotoFlowRecycleViewAdapter(Context context, RecyclerView recyclerView, List<M> data) {
        this.context = context;
        this.cellSize = Utils.getScreenWidth(context) / 2;
        if(data!=null){
            this.data = data;
        }else{
            this.data = new ArrayList<>();;
        }
        this.mRecyclerView = recyclerView;
//        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
//                super.onScrollStateChanged(recyclerView, newState);
//                isScrolling = !(newState == RecyclerView.SCROLL_STATE_IDLE);
//                if (!isScrolling) {
//                    notifyDataSetChanged();
//                }
//            }
//        });
    }
    @Override
    public int getItemViewType(int position) {
        Integer high = mPostCache.get(position);
        if(high==null){
            high = cellSize+ DisplayUtil.dp2px(context, 18 * (a.nextInt(9) / 3 * 3));
            mPostCache.put(position,high);
        }
        LogUtil.i("getItemViewType post = " + position + " h=" + high);
        return high;
    }

    @Override
    public void onBindViewHolder(BasePhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position) {
        StaggeredGridLayoutManager.LayoutParams layoutParams = (StaggeredGridLayoutManager.LayoutParams) holder.itemView.getLayoutParams();
        layoutParams.setFullSpan(false);
        Integer high = mPostCache.get(position);
        LogUtil.i("post = " + position + " h=" + high);
        layoutParams.height = high;
        layoutParams.width = cellSize;
        holder.itemView.setLayoutParams(layoutParams);
        bindPhoto(holder, position);

    }

    private void bindPhoto(final BasePhotoFlowRecycleViewAdapter.BaseViewHolder holder, int position) {
//        if (!lockedAnimations) {
//            if (lastAnimatedItem == holder.getAdapterPosition()) {
//                setLockedAnimations(true);
//            }
            long animationDelay = PHOTO_ANIMATION_DELAY + holder.getAdapterPosition() * 30;
            animatePhoto(holder,animationDelay,position);
//        }
//        if (lastAnimatedItem < position) lastAnimatedItem = position;
    }
    abstract protected void animatePhoto(BaseViewHolder viewHolder,long animationDelay, int position);
    @Override
    public int getItemCount() {
        return data.size();
    }

    public void setLockedAnimations(boolean lockedAnimations) {
        this.lockedAnimations = lockedAnimations;
    }

    public static class BaseViewHolder extends RecyclerView.ViewHolder {
        public BaseViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}
