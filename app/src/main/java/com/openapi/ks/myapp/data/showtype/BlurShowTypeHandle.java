package com.openapi.ks.myapp.data.showtype;

import android.content.Context;
import android.view.View;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.openapi.core.net.netglide.MyGlideModule;
// 使用现代 Shimmer 替代
// import com.romainpiel.shimmer.Shimmer;
import com.openapi.ks.myapp.adapter.IndexRecommendAdapter;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.data.ImagePhotoLoad;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Created by openapi on 2017/11/30.
 */

public class BlurShowTypeHandle extends ShowTypeHandle {
    public void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Object shimmer, boolean needShimmer, Context context) {
        if(vo.img!=null && vo.img.endsWith(".txt")){
            fetchBase64ImageFromUrl(vo.img, holder, context);
            return;
        }
        RequestManager build = ImagePhotoLoad.getBuilder(context, vo.img);
        build.load(vo.img)
                .transition(withCrossFade(500)).apply(ImagePhotoLoad.options).listener(new MyGlideModule.LoggingListener()).into(new DrawableImageViewTarget(holder.ivPhoto));

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
