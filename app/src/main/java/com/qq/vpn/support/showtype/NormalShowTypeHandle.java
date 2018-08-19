package com.qq.vpn.support.showtype;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.view.View;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.bumptech.glide.request.transition.Transition;
import com.qq.ext.util.glide.MyGlideLibModule;
import com.romainpiel.shimmer.Shimmer;
import com.qq.vpn.adapter.RecommendAdapter;
import com.qq.vpn.domain.res.RecommendVo;
import com.qq.vpn.support.ImagePhotoLoad;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Created by dengt on 2017/11/30.
 */

public class NormalShowTypeHandle implements ShowTypeHandle{
    public void loadPhoto(final RecommendAdapter.RecommendItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        RequestManager build = ImagePhotoLoad.getBuilder(context,vo.img);
        build.load(vo.img).apply(ImagePhotoLoad.options).transition(withCrossFade(500)).listener(new MyGlideLibModule.LoggingListener()).into(new DrawableImageViewTarget(holder.ivPhoto) {
            @Override
            public void onResourceReady(Drawable resource, @Nullable Transition<? super Drawable> transition) {
                super.onResourceReady(resource, transition);
                shimmer.cancel();
                holder.ivTitle.setVisibility(View.GONE);
            }
        });
    }
    public void showTitle(final RecommendAdapter.RecommendItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context){
        holder.ivTitle.setVisibility(View.VISIBLE);
        if(needShimmer){
            shimmer.start(holder.ivTitle);
        }
        holder.tvTitleBelow.setVisibility(View.GONE);
    }

}
