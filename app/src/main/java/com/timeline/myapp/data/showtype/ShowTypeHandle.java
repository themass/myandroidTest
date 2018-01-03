package com.timeline.myapp.data.showtype;

import android.content.Context;

import com.romainpiel.shimmer.Shimmer;
import com.timeline.myapp.adapter.IndexRecommendAdapter;
import com.timeline.myapp.bean.vo.RecommendVo;

/**
 * Created by themass on 2017/11/30.
 */

public interface ShowTypeHandle {
        public void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context);
        public void showTitle(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context);
}
