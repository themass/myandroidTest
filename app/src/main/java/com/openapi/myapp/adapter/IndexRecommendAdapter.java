package com.openapi.myapp.adapter;

import com.openapi.common.util.LogUtil;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openapi.myapp.data.ImagePhotoLoad;
import com.romainpiel.shimmer.Shimmer;
import com.romainpiel.shimmer.ShimmerTextView;
import com.openapi.common.helper.OnStartDragListener;
import com.openapi.common.util.DensityUtil;
import com.openapi.common.util.StringUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.myapp.adapter.base.BasePhotoFlowRecycleViewAdapter;
import com.openapi.myapp.bean.vo.RecommendVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.VersionUpdater;
import com.openapi.ks.free1.R;

import java.util.List;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexRecommendAdapter<NaviItemViewHolder> extends BasePhotoFlowRecycleViewAdapter {
    public boolean showEdit;
    private final boolean compactRow;
    StaggeredGridLayoutManager layoutManager;
    private int itemWidth;
    private int imgWidth;
    private int marginPix;
    private int hExtra;
    private boolean needShimmer = true;
    private OnEditClickListener mOnEditClickListener;
    public boolean showParam = false;

    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, StaggeredGridLayoutManager layoutManager, OnRecyclerViewItemClickListener listener, OnStartDragListener dragStartListener, OnEditClickListener onEditClickListener, boolean showEdit) {
        super(context, recyclerView, data, listener, dragStartListener);
        this.showEdit = showEdit;
        // 行布局只看构造时的类型（Sites 为 true）；showEdit 会被 initPullView 与长按切换，不能用来选布局
        this.compactRow = showEdit;
        this.layoutManager = layoutManager;
        this.mOnEditClickListener = onEditClickListener;
        itemWidth = DensityUtil.getDensityDisplayMetrics(context).widthPixels / layoutManager.getSpanCount();
        marginPix = context.getResources().getDimensionPixelSize(R.dimen.margin_2) * 2 + context.getResources().getDimensionPixelSize(R.dimen.margin_3) * 2;
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
        // 可编辑的自定义列表（Sites）用横向的紧凑行；推荐列表保持原卡片
        final int layout = compactRow ? R.layout.tab_index_item_site : R.layout.tab_index_item_recommend;
        final View view = LayoutInflater.from(context).inflate(layout, parent, false);
        return new IndexRecommendAdapter.NaviItemViewHolder(view);
    }

    /** Sites 列表插入广告的位置（第 3 位，position 从 0 起）。 */
    public static final int SITE_AD_POSITION = 2;

    /** 这条数据是不是本地插入的广告位（服务端不会下发这一类）。 */
    private boolean isAdRow(int position) {
        if (position < 0 || position >= data.size()) {
            return false;
        }
        Object o = data.get(position);
        return o instanceof RecommendVo && ((RecommendVo) o).dataType == RecommendVo.dataType_ADS;
    }

    @Override
    public void onCustomeBindViewHolder(final BaseViewHolder holder, final int position) {
        final IndexRecommendAdapter.NaviItemViewHolder viewHolder = (IndexRecommendAdapter.NaviItemViewHolder) holder;
        if (isAdRow(position)) {
            // 广告行：不要编辑按钮、不要标题/网址，整行让给 Banner
            viewHolder.ivEdit.setVisibility(View.GONE);
            viewHolder.ivTitle.setVisibility(View.GONE);
            if (viewHolder.tvTitleBelow != null) {
                viewHolder.tvTitleBelow.setVisibility(View.GONE);
            }
            if (viewHolder.tvUrl != null) {
                viewHolder.tvUrl.setVisibility(View.GONE);
            }
            viewHolder.ivADS.setVisibility(View.GONE);
            if (viewHolder.ivNew != null) {
                viewHolder.ivNew.setVisibility(View.GONE);
            }
            viewHolder.natvieView.setVisibility(View.GONE);
            showRowBanner(viewHolder);
            return;
        }
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
        // 调试：确认 Sites 的 title 是否真的到达客户端（排查标题不显示）
        if (showEdit) {
            LogUtil.i("sites row title=" + vo.title + " url=" + vo.actionUrl);
        }
        if (holder.tvUrl != null) {
            boolean show = StringUtils.hasText(vo.actionUrl) && !vo.adsShow;
            holder.tvUrl.setText(hostOf(vo.actionUrl));
            holder.tvUrl.setVisibility(show ? View.VISIBLE : View.GONE);
        }
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
        // 不再使用后端下发的 vo.color（会出现刺眼的粉色块），统一用深色卡片底。
        holder.ivPhoto.setBackgroundResource(R.drawable.pv_bg_card);
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
        if (compactRow) {
            // Sites 行的标题必须一直可见：showType（Normal/Blur/Text）会把 tv_title_below 隐藏，
            // 这里在 showType 处理之后强制显示，并隐藏叠在图上的大标题，保证"名称在左、网址在右"。
            holder.ivTitle.setVisibility(View.GONE);
            holder.tvTitleBelow.setVisibility(View.VISIBLE);
        }
        holder.natvieView.setVisibility(View.GONE);
        if (holder.mBannerHolder != null) {
            holder.mBannerHolder.removeAllViews();
            holder.mBannerHolder.setVisibility(View.GONE);
        }
    }

    /**
     * Sites 第 3 位的 Banner。RecyclerView 会复用 ViewHolder，所以每次绑定都按行判断：
     * 广告行显示并加载 Banner，普通行隐藏并清空，避免广告跟着复用跑到别的行上。
     */
    private void showRowBanner(IndexRecommendAdapter.NaviItemViewHolder holder) {
        if (holder.mBannerHolder == null) {
            return;
        }
        holder.mBannerHolder.setVisibility(View.VISIBLE);
        android.app.Activity activity = null;
        Context c = holder.itemView.getContext();
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof android.app.Activity) {
                activity = (android.app.Activity) c;
                break;
            }
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        if (activity instanceof androidx.fragment.app.FragmentActivity) {
            com.openapi.yewu.ads.base.AdsManager.getInstans().showBannerAds(
                    (androidx.fragment.app.FragmentActivity) activity, holder.mBannerHolder,
                    com.openapi.yewu.ads.base.AdsContext.Categrey.CATEGREY_VPN,
                    com.openapi.yewu.ads.base.AdsContext.AdsFrom.ADMOB);
        }
    }

    public interface OnEditClickListener {
        void onEditClick(View view, int postion);
    }

    public static class NaviItemViewHolder extends BasePhotoFlowRecycleViewAdapter.BaseViewHolder {
        public CardView cardView;
        public FrameLayout flImg;
        public ShimmerTextView ivTitle;
        public ImageView ivPhoto;
        public ImageView ivEdit;
        public TextView ivADS;
        public View ivNew;
        public TextView tvTitleBelow;
        public TextView tvUrl;
        @Nullable
        RelativeLayout natvieView;
        /** Sites 行的 Banner 容器（第 3 位广告用）。 */
        @Nullable
        ViewGroup mBannerHolder;
        public NaviItemViewHolder(View view) {
            super(view);
            cardView = view.findViewById(R.id.card_view);
            flImg = view.findViewById(R.id.fl_img);
            ivTitle = view.findViewById(R.id.tv_title);
            ivPhoto = view.findViewById(R.id.iv_photo);
            ivEdit = view.findViewById(R.id.iv_edit);
            ivADS = view.findViewById(R.id.ads_logo);
            ivNew = view.findViewById(R.id.iv_new);
            tvTitleBelow = view.findViewById(R.id.tv_title_below);
            tvUrl = view.findViewById(R.id.tv_url);
            natvieView = view.findViewById(R.id.natvieView);
            mBannerHolder = view.findViewById(R.id.fl_row_banner);
        }
    }


    /** 只显示网址的域名部分，列表里更干净；解析失败时原样显示。 */
    private static String hostOf(String url) {
        try {
            String host = android.net.Uri.parse(url).getHost();
            return host != null && !host.isEmpty() ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}
