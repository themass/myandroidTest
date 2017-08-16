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
import com.sspacee.common.helper.OnStartDragListener;
import com.sspacee.common.util.DensityUtil;
import com.sspacee.common.util.StringUtils;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;

import java.util.List;

import butterknife.Bind;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexRecommendAdapter<NaviItemViewHolder> extends BasePhotoFlowRecycleViewAdapter{
    StaggeredGridLayoutManager layoutManager;
    private int itemWidth;
    private int imgWidth;
    private int marginPix;
    private int hExtra;
    private boolean needShimmer= true;
    private OnEditClickListener mOnEditClickListener;
    public boolean showEdit;
    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, StaggeredGridLayoutManager layoutManager ,OnRecyclerViewItemClickListener listener,OnStartDragListener dragStartListener,OnEditClickListener onEditClickListener,boolean showEdit) {
        super(context, recyclerView, data,listener,dragStartListener);
        this.showEdit = showEdit;
        this.layoutManager = layoutManager;
        this.mOnEditClickListener = onEditClickListener;
        itemWidth = DensityUtil.getDensityDisplayMetrics(context).widthPixels / layoutManager.getSpanCount();
        marginPix = context.getResources().getDimensionPixelSize(R.dimen.margin_4) * 2 + context.getResources().getDimensionPixelSize(R.dimen.margin_5) * 2;
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
    public void onCustomeBindViewHolder(final BaseViewHolder holder, final int position) {
        final IndexRecommendAdapter.NaviItemViewHolder viewHolder = (IndexRecommendAdapter.NaviItemViewHolder) holder;
        viewHolder.ivEdit.setVisibility(showEdit?View.VISIBLE:View.GONE);
        viewHolder.ivEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mOnEditClickListener!=null){
                    mOnEditClickListener.onEditClick(viewHolder.itemView,position);
                }
            }
        });
    }
    public void switchFlag(boolean flag){
         if(showEdit!=flag) {
             showEdit = flag;
             notifyDataSetChanged();
         }
    }
    public boolean getSwitchFlag(){
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
        holder.ivPhoto.setBackgroundColor(Color.parseColor(vo.color));
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
        @Bind(R.id.iv_edit)
        public ImageView ivEdit;
        public NaviItemViewHolder(View view) {
            super(view);
        }
    }
    public interface OnEditClickListener{
        public void onEditClick(View view,int postion);
    }

}
