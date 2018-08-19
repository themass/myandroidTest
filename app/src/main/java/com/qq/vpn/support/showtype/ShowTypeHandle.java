package com.qq.vpn.support.showtype;

import android.content.Context;

import com.romainpiel.shimmer.Shimmer;
import com.qq.vpn.adapter.RecommendAdapter;
import com.qq.vpn.domain.res.RecommendVo;

/**
 * Created by dengt on 2017/11/30.
 */

public interface ShowTypeHandle {
        public void loadPhoto(final RecommendAdapter.RecommendItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context);
        public void showTitle(final RecommendAdapter.RecommendItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context);
}
