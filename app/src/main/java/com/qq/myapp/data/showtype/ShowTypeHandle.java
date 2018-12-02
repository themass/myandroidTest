package com.qq.myapp.data.showtype;

import android.content.Context;

import com.romainpiel.shimmer.Shimmer;
import com.qq.myapp.adapter.IndexRecommendAdapter;
import com.qq.myapp.bean.vo.RecommendVo;

/**
 * Created by dengt on 2017/11/30.
 */

public interface ShowTypeHandle {
        public void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context);
        public void showTitle(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context);
}
