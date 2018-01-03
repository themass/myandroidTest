package com.timeline.myapp.data.showtype;

import android.content.Context;
import android.view.View;

import com.romainpiel.shimmer.Shimmer;
import com.timeline.myapp.adapter.IndexRecommendAdapter;
import com.timeline.myapp.bean.vo.RecommendVo;

/**
 * Created by themass on 2017/11/30.
 */

public class TextShowTypeHandle implements ShowTypeHandle{
    public void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        holder.ivPhoto.setVisibility(View.GONE);
    }
    public void showTitle(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        holder.ivTitle.setVisibility(View.VISIBLE);
        if(needShimmer){
            shimmer.start(holder.ivTitle);
        }
        holder.tvTitleBelow.setVisibility(View.GONE);
    }

}
