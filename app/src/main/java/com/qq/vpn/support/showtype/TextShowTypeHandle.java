package com.qq.vpn.support.showtype;

import android.content.Context;
import android.view.View;

import com.romainpiel.shimmer.Shimmer;
import com.qq.vpn.adapter.RecommendAdapter;
import com.qq.vpn.domain.res.RecommendVo;

/**
 * Created by dengt on 2017/11/30.
 */

public class TextShowTypeHandle implements ShowTypeHandle{
    public void loadPhoto(final RecommendAdapter.RecommendItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        holder.ivPhoto.setVisibility(View.GONE);
    }
    public void showTitle(final RecommendAdapter.RecommendItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        holder.ivTitle.setVisibility(View.VISIBLE);
        if(needShimmer){
            shimmer.start(holder.ivTitle);
        }
        holder.tvTitleBelow.setVisibility(View.GONE);
    }

}
