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
public class IndexRecommendAdapter<NaviItemViewHolder> extends BasePhotoFlowRecycleViewAdapter implements View.OnClickListener {
    private static final Random random = new Random();
    StaggeredGridLayoutManager layoutManager;
    private ItemClickListener listener;
    private int itemWidth;
    private int imgWidth;
    private int marginPix;
    private int hExtra;

    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, ItemClickListener listener, StaggeredGridLayoutManager layoutManager) {
        super(context, recyclerView, data);
        this.listener = listener;
        this.layoutManager = layoutManager;
        itemWidth = DensityUtil.getDensityDisplayMetrics(context).widthPixels / layoutManager.getSpanCount();
        marginPix = context.getResources().getDimensionPixelSize(R.dimen.margin_8) * layoutManager.getSpanCount() + context.getResources().getDimensionPixelSize(R.dimen.margin_3) * layoutManager.getSpanCount();
        imgWidth = itemWidth - marginPix;
        hExtra = context.getResources().getDimensionPixelSize(R.dimen.margin_3);
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
        holder.itemView.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (listener != null) {
            listener.onItemClick(v, (int) v.getTag());
        }
    }

    @Override
    protected void animatePhoto(BaseViewHolder viewHolder, long animationDelay, int position) {
        final IndexRecommendAdapter.NaviItemViewHolder holder = (IndexRecommendAdapter.NaviItemViewHolder) viewHolder;
        final RecommendVo vo = (RecommendVo) data.get(position);
        if (vo.title.length() > 9) {
            holder.ivTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.textsize_25));
        } else {
            holder.ivTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.textsize_32));
        }
        holder.ivTitle.setText(vo.title);
        if (StringUtils.hasText(vo.color)) {
            holder.ivPhoto.setBackgroundColor(Color.parseColor(vo.color));
        } else {
            holder.ivPhoto.setBackgroundResource(R.color.style_color_primary_trans);
        }
        ViewGroup.LayoutParams ivPhotoParam = holder.ivPhoto.getLayoutParams();
        ivPhotoParam.height = (int) (imgWidth * vo.rate);
        ivPhotoParam.width = imgWidth;
        holder.ivPhoto.setLayoutParams(ivPhotoParam);

        holder.ivTitle.setVisibility(View.VISIBLE);
        final Shimmer shimmer = new Shimmer();
        shimmer.setDuration(Constants.RECOMMAND_SHIMMER_DURATION);
        shimmer.start(holder.ivTitle);
        IndexRecommendPhotoLoad.loadPhoto(holder,vo,shimmer,context);
    }

    public interface ItemClickListener {
        public void onItemClick(View v, int position);
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
}
