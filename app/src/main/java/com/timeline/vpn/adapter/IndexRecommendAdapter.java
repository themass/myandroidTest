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
import android.widget.TextView;
import android.widget.Toast;

import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;
import com.sspacee.common.helper.OnStartDragListener;
import com.sspacee.common.util.DensityUtil;
import com.sspacee.common.util.StringUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.ImagePhotoLoad;
import com.timeline.vpn.data.VersionUpdater;

import java.util.List;

import butterknife.BindView;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexRecommendAdapter<NaviItemViewHolder> extends BasePhotoFlowRecycleViewAdapter {
    public boolean showEdit;
    StaggeredGridLayoutManager layoutManager;
    private int itemWidth;
    private int imgWidth;
    private int marginPix;
    private int hExtra;
    private boolean needShimmer = true;
    private OnEditClickListener mOnEditClickListener;

    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, StaggeredGridLayoutManager layoutManager, OnRecyclerViewItemClickListener listener, OnStartDragListener dragStartListener, OnEditClickListener onEditClickListener, boolean showEdit) {
        super(context, recyclerView, data, listener, dragStartListener);
        this.showEdit = showEdit;
        this.layoutManager = layoutManager;
        this.mOnEditClickListener = onEditClickListener;
        itemWidth = DensityUtil.getDensityDisplayMetrics(context).widthPixels / layoutManager.getSpanCount();
        marginPix = context.getResources().getDimensionPixelSize(R.dimen.margin_4) * 2 + context.getResources().getDimensionPixelSize(R.dimen.margin_5) * 2;
        imgWidth = itemWidth - marginPix;
        hExtra = context.getResources().getDimensionPixelSize(R.dimen.margin_3);
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
        final View view = LayoutInflater.from(context).inflate(R.layout.tab_index_item_recommend, parent, false);
        return new IndexRecommendAdapter.NaviItemViewHolder(view);
    }

    @Override
    public void onCustomeBindViewHolder(final BaseViewHolder holder, final int position) {
        final IndexRecommendAdapter.NaviItemViewHolder viewHolder = (IndexRecommendAdapter.NaviItemViewHolder) holder;
        viewHolder.ivEdit.setVisibility(showEdit ? View.VISIBLE : View.GONE);
        viewHolder.ivEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RecommendVo vo = (RecommendVo) data.get(position);
                if (StringUtils.hasText(vo.minVersion)) {
                    if (VersionUpdater.getVersion().compareTo(vo.minVersion) < 0) {
                        Toast.makeText(context, R.string.version_low, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                if (mOnEditClickListener != null) {
                    mOnEditClickListener.onEditClick(viewHolder.itemView, position);
                }
            }
        });
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
        final IndexRecommendAdapter.NaviItemViewHolder holder = (IndexRecommendAdapter.NaviItemViewHolder) viewHolder;
        final RecommendVo vo = (RecommendVo) data.get(position);
        holder.ivPhoto.setImageResource(0);
        if (vo.title.length() > 9) {
            holder.ivTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.textsize_25));
        } else {
            holder.ivTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.textsize_32));
        }
        holder.ivTitle.setText(vo.title);
        if (!StringUtils.hasText(vo.color)) {
            vo.color = Constants.colorBg.get(position % Constants.colorBg.size());
        }
        if (vo.dataType == RecommendVo.dataType_ADS) {
            holder.ivADS.setVisibility(View.VISIBLE);
        } else {
            holder.ivADS.setVisibility(View.GONE);
        }
        holder.ivPhoto.setBackgroundColor(Color.parseColor(vo.color));
        ViewGroup.LayoutParams ivPhotoParam = holder.ivPhoto.getLayoutParams();
        ivPhotoParam.height = (int) (imgWidth * vo.rate);
        ivPhotoParam.width = imgWidth;
        holder.ivPhoto.setLayoutParams(ivPhotoParam);
        holder.ivTitle.setVisibility(View.VISIBLE);
        final Shimmer shimmer = new Shimmer();
        if (needShimmer) {
            shimmer.setDuration(Constants.RECOMMAND_SHIMMER_DURATION);
            shimmer.start(holder.ivTitle);
        }
        ImagePhotoLoad.loadPhoto(holder, vo, shimmer, context);
    }

    public interface OnEditClickListener {
        void onEditClick(View view, int postion);
    }

    public static class NaviItemViewHolder extends BasePhotoFlowRecycleViewAdapter.BaseViewHolder {
        @BindView(R.id.card_view)
        public CardView cardView;
        @BindView(R.id.tv_title)
        public ShimmerTextView ivTitle;
        @BindView(R.id.iv_photo)
        public ImageView ivPhoto;
        @BindView(R.id.iv_edit)
        public ImageView ivEdit;
        @BindView(R.id.ads_logo)
        public TextView ivADS;

        public NaviItemViewHolder(View view) {
            super(view);
        }
    }

}
