package com.qq.vpn.adapter;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.qq.ext.util.DensityUtil;
import com.qq.ext.util.StringUtils;
import com.qq.fq3.R;
import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;
import com.qq.Constants;
import com.qq.vpn.adapter.base.PhotoFlowRecycleViewAdapter;
import com.qq.vpn.domain.res.RecommendVo;
import com.qq.vpn.support.ImagePhotoLoad;

import java.util.List;

import butterknife.BindView;

/**
 * Created by dengt on 14-03-12.
 */
public class RecommendAdapter<RecommendItemViewHolder> extends PhotoFlowRecycleViewAdapter {
    public boolean showEdit;
    StaggeredGridLayoutManager layoutManager;
    private int itemWidth;
    private int imgWidth;
    private int marginPix;
    private int hExtra;
    private boolean needShimmer = true;
    private OnEditClickListener mOnEditClickListener;
    public boolean showParam = false;
    public RecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, StaggeredGridLayoutManager layoutManager, OnRecyclerViewItemClickListener listener) {
        super(context, recyclerView, data, listener);
        this.layoutManager = layoutManager;
        itemWidth = DensityUtil.getDensityDisplayMetrics(context).widthPixels / layoutManager.getSpanCount();
        marginPix = context.getResources().getDimensionPixelSize(R.dimen.margin_4) * 2 + context.getResources().getDimensionPixelSize(R.dimen.margin_5) * 2;
        imgWidth = itemWidth - marginPix;
        hExtra = context.getResources().getDimensionPixelSize(R.dimen.margin_3);
    }
    public void setShowParam(boolean show){
        showParam = show;
    }
    public void setNeedShimmer(boolean needShimmer) {
        this.needShimmer = needShimmer;
    }

    @Override
    public int getItemViewType(int position) {
        RecommendVo vo = (RecommendVo) data.get(position);
        return vo.showType;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(context).inflate(R.layout.layout_recommend, parent, false);
        return new RecommendAdapter.RecommendItemViewHolder(view);
    }

    @Override
    public void onCustomeBindViewHolder(final BaseViewHolder holder, final int position) {
        final RecommendAdapter.RecommendItemViewHolder viewHolder = (RecommendAdapter.RecommendItemViewHolder) holder;
    }

    public void switchFlag(boolean flag) {
        if (showEdit != flag) {
            showEdit = flag;
            notifyDataSetChanged();
        }
    }

    public boolean getSwitchFlag() {
        return showEdit;
    }

    @Override
    protected void animatePhoto(BaseViewHolder viewHolder, long animationDelay, int position) {
        final RecommendAdapter.RecommendItemViewHolder holder = (RecommendAdapter.RecommendItemViewHolder) viewHolder;
        final RecommendVo vo = (RecommendVo) data.get(position);
        holder.ivPhoto.setImageResource(0);
        holder.ivTitle.setText(vo.title);
        holder.tvTitleBelow.setText(vo.title);
        if (!StringUtils.hasText(vo.color)) {
            vo.color = Constants.colorBg.get(position % Constants.colorBg.size());
        }
        holder.ivPhoto.setBackgroundColor(Color.parseColor(vo.color));
        ViewGroup.LayoutParams ivPhotoParam = holder.ivPhoto.getLayoutParams();
        ivPhotoParam.height = (int) (imgWidth * vo.rate);
        ivPhotoParam.width = imgWidth;
        holder.ivPhoto.setLayoutParams(ivPhotoParam);
        final Shimmer shimmer = new Shimmer();
        shimmer.setDuration(Constants.SHIMMER_DURATION);
        holder.ivTitle.setVisibility(View.VISIBLE);
        ImagePhotoLoad.loadPhoto(holder, vo, shimmer,needShimmer, context);
    }

    public interface OnEditClickListener {
        void onEditClick(View view, int postion);
    }

    public static class RecommendItemViewHolder extends PhotoFlowRecycleViewAdapter.BaseViewHolder {
        @BindView(R.id.card_view)
        public CardView cardView;
        @BindView(R.id.tv_title)
        public ShimmerTextView ivTitle;
        @BindView(R.id.iv_photo)
        public ImageView ivPhoto;
        @BindView(R.id.tv_title_below)
        public TextView tvTitleBelow;
        public RecommendItemViewHolder(View view) {
            super(view);
        }
    }

}
