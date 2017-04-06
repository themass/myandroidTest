package com.timeline.vpn.adapter;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;
import com.sspacee.common.util.DensityUtil;
import com.sspacee.common.util.StringUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;

import java.util.List;
import java.util.Random;

import butterknife.Bind;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexRecommendAdapter<NaviItemViewHolder> extends BasePhotoFlowRecycleViewAdapter{
    private static final Random random = new Random();
    StaggeredGridLayoutManager layoutManager;
    public OnRecyclerViewItemClickListener mOnItemClickListener = null;//点击
    public OnRecyclerViewLongItemClickListener mOnLongItemClickListener = null;//长按
    private int itemWidth;
    private int imgWidth;
    private int marginPix;
    private int hExtra;
    private boolean needShimmer= true;

    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, OnRecyclerViewItemClickListener listener,OnRecyclerViewLongItemClickListener longItemClickListener, StaggeredGridLayoutManager layoutManager) {
        super(context, recyclerView, data);
        this.mOnItemClickListener = listener;
        this.mOnLongItemClickListener = longItemClickListener;
        this.layoutManager = layoutManager;
        itemWidth = DensityUtil.getDensityDisplayMetrics(context).widthPixels / layoutManager.getSpanCount();
        marginPix = context.getResources().getDimensionPixelSize(R.dimen.margin_4) * 2 + context.getResources().getDimensionPixelSize(R.dimen.margin_3) * 2;
        imgWidth = itemWidth - marginPix;
        hExtra = context.getResources().getDimensionPixelSize(R.dimen.margin_3);
    }
    public void setNeedShimmer(boolean needShimmer){
        this.needShimmer = needShimmer;
    }
    @Override
    public int getItemViewType(int position) {
        RecommendVo vo = (RecommendVo) data.get(position);
        return vo.showType;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(context).inflate(R.layout.tab_index_item_recommend, parent, false);
        return new IndexRecommendAdapter.NaviItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(BaseViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
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
                if (mOnLongItemClickListener != null) {
                    mOnLongItemClickListener.onLongItemClick(v, (int) v.getTag());
                }
                return true;
            }
        });
    }
    @Override
    protected void animatePhoto(BaseViewHolder viewHolder, long animationDelay, int position) {
        final IndexRecommendAdapter.NaviItemViewHolder holder = (IndexRecommendAdapter.NaviItemViewHolder) viewHolder;
        final RecommendVo vo = (RecommendVo) data.get(position);
        holder.ivPhoto.setImageResource(0);
        if (vo.title.length() > 9) {
            holder.ivTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.textsize_25));
        } else {
            holder.ivTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.textsize_32));
        }
        holder.ivTitle.setText(vo.title);
        if (StringUtils.hasText(vo.color)) {
            holder.ivPhoto.setBackgroundColor(Color.parseColor(vo.color));
        } else {
            String defColor = Constants.colorBg.get(position%Constants.colorBg.size());
            holder.ivPhoto.setBackgroundColor(Color.parseColor(defColor));
        }
        ViewGroup.LayoutParams ivPhotoParam = holder.ivPhoto.getLayoutParams();
        ivPhotoParam.height = (int) (imgWidth * vo.rate);
        ivPhotoParam.width = imgWidth;
        holder.ivPhoto.setLayoutParams(ivPhotoParam);
        holder.ivTitle.setVisibility(View.VISIBLE);
        final Shimmer shimmer = new Shimmer();
        if(needShimmer) {
            shimmer.setDuration(Constants.RECOMMAND_SHIMMER_DURATION);
            shimmer.start(holder.ivTitle);
        }
        IndexRecommendPhotoLoad.loadPhoto(holder, vo, shimmer, context);
    }

    static class NaviItemViewHolder extends BasePhotoFlowRecycleViewAdapter.BaseViewHolder {
        @Bind(R.id.card_view)
        CardView cardView;
        @Bind(R.id.tv_title)
        ShimmerTextView ivTitle;
        @Bind(R.id.iv_photo)
        ImageView ivPhoto;

        public NaviItemViewHolder(View view) {
            super(view);
        }
    }
    public interface OnRecyclerViewLongItemClickListener {
        void onLongItemClick(View view, int position);
    }

    public interface OnRecyclerViewItemClickListener {
        void onItemClick(View view, int position);
    }
}
