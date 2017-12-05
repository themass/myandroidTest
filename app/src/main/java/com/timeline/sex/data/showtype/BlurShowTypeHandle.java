package com.timeline.sex.data.showtype;

import android.content.Context;
import android.view.View;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.romainpiel.shimmer.Shimmer;
import com.sspacee.common.util.MyGlideLibModule;
import com.timeline.sex.adapter.IndexRecommendAdapter;
import com.timeline.sex.bean.vo.RecommendVo;
import com.timeline.sex.data.ImagePhotoLoad;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Created by themass on 2017/11/30.
 */

public class BlurShowTypeHandle implements ShowTypeHandle{
    public void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        RequestManager build = ImagePhotoLoad.getBuilder(context,vo.img);
        build.load(vo.img)
                    .transition(withCrossFade(500)).apply(ImagePhotoLoad.options).listener(new MyGlideLibModule.LoggingListener()).into(new DrawableImageViewTarget(holder.ivPhoto));

    }
    public void showTitle(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        holder.ivTitle.setVisibility(View.VISIBLE);
        if(needShimmer){
            shimmer.start(holder.ivTitle);
        }
        holder.tvTitleBelow.setVisibility(View.GONE);
    }

}
