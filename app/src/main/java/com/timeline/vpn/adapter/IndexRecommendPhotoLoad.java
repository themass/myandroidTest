package com.timeline.vpn.adapter;

import android.content.Context;
import android.view.View;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.romainpiel.shimmer.Shimmer;
import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.Constants;

import jp.wasabeef.glide.transformations.BlurTransformation;

/**
 * Created by gqli on 2017/2/6.
 */
public class IndexRecommendPhotoLoad {
    public static  void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo,final Shimmer shimmer,Context context){
        if(vo.showType== Constants.ShowType.Normal) {
            Glide.with(context).load(vo.img)
                    //                .placeholder(R.drawable.vpn_trans_default)
                    .fitCenter().crossFade().listener(new LoggingListener()).into(new GlideDrawableImageViewTarget(holder.ivPhoto) {
                @Override
                public void onResourceReady(GlideDrawable resource, GlideAnimation<? super GlideDrawable> animation) {
                    super.onResourceReady(resource, animation);
                    shimmer.cancel();
                    holder.ivTitle.setVisibility(View.GONE);
                }
            });
        }else if(vo.showType== Constants.ShowType.Blur){
            Glide.with(context).load(vo.img).placeholder(R.drawable.vpn_trans_default)
                    .fitCenter().crossFade().bitmapTransform(new BlurTransformation(context,23,4)).listener(new LoggingListener()).into(new GlideDrawableImageViewTarget(holder.ivPhoto) {
                @Override
                public void onResourceReady(GlideDrawable resource, GlideAnimation<? super GlideDrawable> animation) {
                    super.onResourceReady(resource, animation);
                }
            });
        }else{

        }
    }
}
