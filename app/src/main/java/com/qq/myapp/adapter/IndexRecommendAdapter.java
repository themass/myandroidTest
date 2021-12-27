package com.qq.myapp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.qq.common.util.PreferenceUtils;
import com.qq.myapp.data.ImagePhotoLoad;
import com.qq.yewu.ads.base.GdtNativeManager;
import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;
import com.qq.common.helper.OnStartDragListener;
import com.qq.common.util.DensityUtil;
import com.qq.common.util.StringUtils;
import com.qq.common.util.ToastUtil;
import com.qq.myapp.adapter.base.BasePhotoFlowRecycleViewAdapter;
import com.qq.myapp.bean.vo.RecommendVo;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.data.VersionUpdater;
import com.qq.ks1.R;

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
    public boolean showParam = false;
    GdtNativeManager gdtNativeManager;

    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, StaggeredGridLayoutManager layoutManager, OnRecyclerViewItemClickListener listener, OnStartDragListener dragStartListener, OnEditClickListener onEditClickListener, boolean showEdit,GdtNativeManager gdtNativeManager) {
        super(context, recyclerView, data, listener, dragStartListener);
        this.showEdit = showEdit;
        this.layoutManager = layoutManager;
        this.mOnEditClickListener = onEditClickListener;
        itemWidth = DensityUtil.getDensityDisplayMetrics(context).widthPixels / layoutManager.getSpanCount();
        marginPix = context.getResources().getDimensionPixelSize(R.dimen.margin_2) * 2 + context.getResources().getDimensionPixelSize(R.dimen.margin_3) * 2;
        imgWidth = itemWidth - marginPix;
        hExtra = context.getResources().getDimensionPixelSize(R.dimen.margin_3);
        this.gdtNativeManager =gdtNativeManager;

    }
    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, StaggeredGridLayoutManager layoutManager, OnRecyclerViewItemClickListener listener, OnStartDragListener dragStartListener, OnEditClickListener onEditClickListener, boolean showEdit) {
        this(context,recyclerView,data,layoutManager,listener,dragStartListener,onEditClickListener,showEdit,null);
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
                        ToastUtil.showShort( R.string.version_low);
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
        holder.ivTitle.setText(vo.title);
        holder.tvTitleBelow.setText(vo.title);
        if (!StringUtils.hasText(vo.color)) {
            vo.color = Constants.colorBg.get(position % Constants.colorBg.size());
        }
        if (vo.dataType == RecommendVo.dataType_ADS) {
            holder.ivADS.setVisibility(View.VISIBLE);
        } else if(showParam && StringUtils.hasText(vo.showLogo)){
            holder.ivADS.setVisibility(View.VISIBLE);
            holder.ivADS.setText(vo.showLogo);
        }else {
            holder.ivADS.setVisibility(View.GONE);
        }
        if (vo.newShow !=null && vo.newShow==true) {
            holder.ivNew.setVisibility(View.VISIBLE);
            holder.ivNew.setBackgroundResource(R.drawable.ic_new);
        } else {
            if(vo.type==0) {
                holder.ivNew.setVisibility(View.GONE);
            }else if(vo.type==1){
                holder.ivNew.setVisibility(View.VISIBLE);
                holder.ivNew.setBackgroundResource(R.drawable.ic_vip1);
            }else if(vo.type==2){
                holder.ivNew.setVisibility(View.VISIBLE);
                holder.ivNew.setBackgroundResource(R.drawable.ic_vip2);
            }else if(vo.type==3){
                holder.ivNew.setVisibility(View.VISIBLE);
                holder.ivNew.setBackgroundResource(R.drawable.ic_vip3);
            }else if(vo.type==4){
                holder.ivNew.setVisibility(View.VISIBLE);
                holder.ivNew.setBackgroundResource(R.drawable.ic_vip4);
            }
        }
        holder.ivPhoto.setBackgroundColor(Color.parseColor(vo.color));
        if(vo.rate==0){
            ViewGroup.LayoutParams ivPhotoParam = holder.ivPhoto.getLayoutParams();
            ivPhotoParam.width = imgWidth;
            ivPhotoParam.height = context.getResources().getDimensionPixelSize(R.dimen.banner_h);
            holder.ivPhoto.setLayoutParams(ivPhotoParam);
        }else {
            ViewGroup.LayoutParams ivPhotoParam = holder.ivPhoto.getLayoutParams();
            ivPhotoParam.height = (int) (imgWidth * vo.rate);
            ivPhotoParam.width = imgWidth;
            holder.ivPhoto.setLayoutParams(ivPhotoParam);
        }
        final Shimmer shimmer = new Shimmer();
        shimmer.setDuration(Constants.RECOMMAND_SHIMMER_DURATION);
        holder.ivTitle.setVisibility(View.VISIBLE);
        ImagePhotoLoad.loadPhoto(holder, vo, shimmer,needShimmer, context);
        if(gdtNativeManager!=null){
            if(!gdtNativeManager.showAds(position,holder.natvieView)){
                holder.natvieView.setVisibility(View.GONE);
            }else{
                holder.natvieView.setVisibility(View.VISIBLE);
            }
        }else{
            holder.natvieView.setVisibility(View.GONE);
        }
    }

    public interface OnEditClickListener {
        void onEditClick(View view, int postion);
    }

    public static class NaviItemViewHolder extends BasePhotoFlowRecycleViewAdapter.BaseViewHolder {
        @BindView(R.id.card_view)
        public CardView cardView;
        @BindView(R.id.fl_img)
        public FrameLayout flImg;
        @BindView(R.id.tv_title)
        public ShimmerTextView ivTitle;
        @BindView(R.id.iv_photo)
        public ImageView ivPhoto;
        @BindView(R.id.iv_edit)
        public ImageView ivEdit;
        @BindView(R.id.ads_logo)
        public TextView ivADS;
        @BindView(R.id.iv_new)
        public View ivNew;
        @BindView(R.id.tv_title_below)
        public TextView tvTitleBelow;
        @Nullable
        @BindView(R.id.natvieView)
        RelativeLayout natvieView;
        public NaviItemViewHolder(View view) {
            super(view);
        }
    }

}
