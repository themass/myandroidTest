package com.openapi.myapp.data.showtype;

import android.content.Context;

import com.romainpiel.shimmer.Shimmer;
import com.openapi.myapp.adapter.IndexRecommendAdapter;
import com.openapi.myapp.bean.vo.RecommendVo;

/**
 * Created by dengt on 2017/11/30.
 */

public interface ShowTypeHandle {
        public void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context);
        public void showTitle(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context);
}
