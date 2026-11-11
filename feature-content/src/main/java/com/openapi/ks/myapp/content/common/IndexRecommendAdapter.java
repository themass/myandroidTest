package com.openapi.ks.myapp.content.common;

import static com.luck.picture.lib.thread.PictureThreadUtils.runOnUiThread;

import android.content.Context;
import android.graphics.Color;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.lang.VersionCompare;
// 使用现代 Shimmer 替代
import com.facebook.shimmer.ShimmerFrameLayout;
import com.openapi.common.view.touchhelper.OnStartDragListener;
import com.openapi.common.util.android.ScreenSize;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.core.R;
import com.openapi.core.databinding.TabIndexItemRecommendBinding;
import com.openapi.ks.myapp.content.common.BasePhotoFlowRecycleViewAdapter;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.Constants;
import com.openapi.ks.myapp.content.common.ShowTypeContext;
import com.openapi.ks.myapp.data.VersionUpdater;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Miroslaw Stanek on 20.01.15.
 */
public class IndexRecommendAdapter<NaviItemViewHolder> extends BasePhotoFlowRecycleViewAdapter {
    public boolean showEdit;
    private final RecyclerView recyclerView;
    RecyclerView.LayoutManager layoutManager;
    private int itemWidth;
    private int imgWidth;
    private int marginPix;
    private int hExtra;
    private boolean needShimmer = true;
    private OnEditClickListener mOnEditClickListener;
    public boolean showParam = false;

    public IndexRecommendAdapter(Context context, RecyclerView recyclerView, List<RecommendVo> data, RecyclerView.LayoutManager layoutManager, OnRecyclerViewItemClickListener listener, OnStartDragListener dragStartListener, OnEditClickListener onEditClickListener, boolean showEdit) {
        super(context, recyclerView, data, listener, dragStartListener);
        this.recyclerView = recyclerView;
        this.showEdit = showEdit;
        this.layoutManager = layoutManager;
        this.mOnEditClickListener = onEditClickListener;
        int screenW = ScreenSize.metrics(context).widthPixels;
        if (layoutManager instanceof StaggeredGridLayoutManager) {
            itemWidth = screenW / ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
        } else if (layoutManager instanceof GridLayoutManager) {
            itemWidth = screenW / ((GridLayoutManager) layoutManager).getSpanCount();
        } else {
            itemWidth = screenW / 3;
        }
        // 与 tab_index_item_recommend 一致：卡片左右各 margin_2，无 compat padding、无水平内边距
        int insetH = context.getResources().getDimensionPixelSize(R.dimen.margin_2) * 2;
        marginPix = insetH;
        imgWidth = itemWidth - marginPix;
        hExtra = context.getResources().getDimensionPixelSize(R.dimen.margin_3);
    }

    private int computeImageWidth(int position) {
        int rw = recyclerView.getWidth();
        if (rw <= 0) {
            rw = ScreenSize.metrics(context).widthPixels;
        }
        rw -= recyclerView.getPaddingLeft() + recyclerView.getPaddingRight();
        if (rw <= 0) {
            rw = ScreenSize.metrics(context).widthPixels;
        }
        if (layoutManager instanceof GridLayoutManager) {
            GridLayoutManager glm = (GridLayoutManager) layoutManager;
            int span = glm.getSpanSizeLookup().getSpanSize(position);
            int total = glm.getSpanCount();
            return Math.max(1, rw * span / total - marginPix);
        }
        if (layoutManager instanceof StaggeredGridLayoutManager) {
            int cols = ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
            return Math.max(1, rw / cols - marginPix);
        }
        return Math.max(1, imgWidth);
    }

    public void setShowParam(boolean show) {
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
        // 版本检查的排查日志：item 出现时打印已安装版本、服务端 minVersion 和比较结果
        RecommendVo shown = (RecommendVo) data.get(position);
        String installedVersion = VersionUpdater.getVersion();
        LogUtil.i("channel item shown id=" + shown.id + " title=" + shown.title + " installed=" + installedVersion
                + " minVersion=" + shown.minVersion + " cmp="
                + (StringUtils.hasText(shown.minVersion) ? VersionCompare.compare(installedVersion, shown.minVersion) : "n/a"));
        viewHolder.ivEdit.setVisibility(showEdit ? View.VISIBLE : View.GONE);
        viewHolder.ivEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RecommendVo vo = (RecommendVo) data.get(position);
                if (StringUtils.hasText(vo.minVersion)) {
                    String installed = VersionUpdater.getVersion();
                    int cmp = VersionCompare.compare(installed, vo.minVersion);
                    LogUtil.i("channel version check id=" + vo.id + " title=" + vo.title + " installed=" + installed
                            + " minVersion=" + vo.minVersion + " cmp=" + cmp);
                    if (cmp < 0) {
                        ToastUtil.showShort(R.string.version_low);
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
        if (!StringUtils.hasText(vo.


                color)) {
            vo.color = Constants.colorBg.get(position % Constants.colorBg.size());
        }
        if (vo.dataType == RecommendVo.dataType_ADS) {
            holder.ivADS.setVisibility(View.VISIBLE);
        } else if (showParam && StringUtils.hasText(vo.showLogo)) {
            holder.ivADS.setVisibility(View.VISIBLE);
            holder.ivADS.setText(vo.showLogo);
        } else {
            holder.ivADS.setVisibility(View.GONE);
        }
        if (vo.dataType == RecommendVo.dataType_VIDEO_CHANNEL || vo.dataType == RecommendVo.dataType_IMG_CHANNEL) {
            boolean picShow = PreferenceUtils.getPrefBoolean(context, Constants.VIDEO_PIC_SWITCH, true);
            if (!picShow) {
                vo.showType = Constants.ShowType.Normal;
                vo.img = "";
                if (vo.rate >= 1) {
                    vo.rate = 0.8f;
                }
            }
        }
        if (vo.newShow != null && vo.newShow == true) {
            holder.ivNew.setVisibility(View.VISIBLE);
            holder.ivNew.setBackgroundResource(R.drawable.ic_new);
        } else {
            if (vo.type == 0) {
                holder.ivNew.setVisibility(View.GONE);
            } else if (vo.type == 1) {
                holder.ivNew.setVisibility(View.VISIBLE);
                holder.ivNew.setBackgroundResource(R.drawable.ic_vip1);
            } else if (vo.type == 2) {
                holder.ivNew.setVisibility(View.VISIBLE);
                holder.ivNew.setBackgroundResource(R.drawable.ic_vip2);
            } else if (vo.type == 3) {
                holder.ivNew.setVisibility(View.VISIBLE);
                holder.ivNew.setBackgroundResource(R.drawable.ic_vip3);
            } else if (vo.type == 4) {
                holder.ivNew.setVisibility(View.VISIBLE);
                holder.ivNew.setBackgroundResource(R.drawable.ic_vip4);
            }
        }
        holder.ivPhoto.setBackgroundColor(Color.parseColor(vo.color));
        ViewGroup.LayoutParams itemLp = holder.itemView.getLayoutParams();
        if (itemLp instanceof StaggeredGridLayoutManager.LayoutParams) {
            ((StaggeredGridLayoutManager.LayoutParams) itemLp).setFullSpan(false);
            holder.itemView.setLayoutParams(itemLp);
        }
        int w = computeImageWidth(position);
        ViewGroup.LayoutParams ivPhotoParam = holder.ivPhoto.getLayoutParams();
        ivPhotoParam.height = (int) (w * vo.rate);
        ivPhotoParam.width = w;
        holder.ivPhoto.setLayoutParams(ivPhotoParam);
        // 使用现代 Shimmer 替代
        // final Shimmer shimmer = new Shimmer();
        // shimmer.setDuration(Constants.RECOMMAND_SHIMMER_DURATION);
        holder.ivTitle.setVisibility(View.VISIBLE);
        ShowTypeContext.loadPhoto(holder, vo, null, needShimmer, context);
    }

    public interface OnEditClickListener {
        void onEditClick(View view, int postion);
    }
    public static class NaviItemViewHolder extends BaseViewHolder {
        public CardView cardView;
        public TextView ivTitle; // 使用普通 TextView 替代 ShimmerTextView
        public ImageView ivPhoto;
        public ImageView ivEdit;
        public TextView ivADS;
        public View ivNew;
        public TextView tvTitleBelow;

        public NaviItemViewHolder(View view) {
            super(view);
            TabIndexItemRecommendBinding b = TabIndexItemRecommendBinding.bind(view);
            cardView = b.cardView;
            ivTitle = b.tvTitle;
            ivPhoto = b.ivPhoto;
            ivEdit = b.ivEdit;
            ivADS = b.adsLogo;
            ivNew = b.ivNew;
            tvTitleBelow = b.tvTitleBelow;
        }
    }

}
