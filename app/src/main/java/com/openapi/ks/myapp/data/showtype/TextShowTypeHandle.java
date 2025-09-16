package com.openapi.ks.myapp.data.showtype;

import android.content.Context;
import android.view.View;

// 使用现代 Shimmer 替代
// import com.romainpiel.shimmer.Shimmer;
import com.openapi.ks.myapp.adapter.IndexRecommendAdapter;
import com.openapi.ks.myapp.bean.vo.RecommendVo;

/**
 * Created by openapi on 2017/11/30.
 */

public class TextShowTypeHandle extends ShowTypeHandle {
    public void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Object shimmer, boolean needShimmer, Context context) {
        holder.ivPhoto.setVisibility(View.GONE);
    }

    public void showTitle(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Object shimmer, boolean needShimmer, Context context) {
        holder.ivTitle.setVisibility(View.VISIBLE);
        // 使用现代 Shimmer 替代
        // if (needShimmer) {
        //     shimmer.start(holder.ivTitle);
        // }
        holder.tvTitleBelow.setVisibility(View.GONE);
    }

}
