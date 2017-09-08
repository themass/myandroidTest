package com.timeline.vpn.data;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.bumptech.glide.request.target.DrawableThumbnailImageViewTarget;
import com.bumptech.glide.request.transition.Transition;
import com.etiennelawlor.imagegallery.library.adapters.FullScreenImageGalleryAdapter;
import com.etiennelawlor.imagegallery.library.adapters.ImageGalleryAdapter;
import com.qq.e.comm.util.StringUtil;
import com.romainpiel.shimmer.Shimmer;
import com.sspacee.common.util.MyBlurTransformation;
import com.sspacee.common.util.MyGlideLibModule;
import com.timeline.vpn.R;
import com.timeline.vpn.adapter.IndexRecommendAdapter;
import com.timeline.vpn.bean.vo.RecommendVo;
import com.timeline.vpn.constant.BaseRes;
import com.timeline.vpn.constant.Constants;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Created by gqli on 2017/2/6.
 */
public class ImagePhotoLoad implements ImageGalleryAdapter.ImageThumbnailLoader, FullScreenImageGalleryAdapter.FullScreenImageLoader {
    public ImagePhotoLoad(Context context) {
    }

    public static void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, Context context) {
        if (!StringUtil.isEmpty(vo.img))
            if (vo.showType == Constants.ShowType.Normal) {
                RequestOptions options = new RequestOptions().fitCenter()
                        .priority(Priority.HIGH);
                RequestManager build = Glide.with(context);
                if(vo.img.endsWith(".gif")){
                    build.asGif();
                }
                build.load(vo.img).apply(options).transition(withCrossFade(500)).listener(new MyGlideLibModule.LoggingListener()).into(new DrawableImageViewTarget(holder.ivPhoto) {
                    @Override
                    public void onResourceReady(Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        super.onResourceReady(resource, transition);
                        shimmer.cancel();
                        holder.ivTitle.setVisibility(View.GONE);
                    }
                });
            } else if (vo.showType == Constants.ShowType.Blur) {
                RequestOptions options = new RequestOptions().fitCenter().skipMemoryCache(true)
                        .priority(Priority.HIGH).transform(new MyBlurTransformation(context));
                Glide.with(context).load(vo.img)
                        .transition(withCrossFade(500)).apply(options).listener(new MyGlideLibModule.LoggingListener()).into(new DrawableImageViewTarget(holder.ivPhoto));
            }
    }

    public static void getCountryImage(Context context, ImageView iv, String url) {
        if (iv == null || StringUtil.isEmpty(url)) {
            return;
        }
        if (url.startsWith(Constants.IMAGE_RES_PRE)) {
            url = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf("."));
            iv.setImageResource(BaseRes.img.get(url));
        } else {
            RequestOptions options = new RequestOptions()
                    .centerCrop()
                    .placeholder(R.drawable.vpn_trans_default)
                    .priority(Priority.HIGH);
            Glide.with(context)
                    .load(iv).apply(options)
                    .into(iv);
        }
    }

    @Override
    public void loadImageThumbnail(ImageView iv, String imageUrl, int dimension) {
//        imageThumbnailLoader.loadImageThumbnail(iv, imageUrl, dimension);
        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.img_bg)
                .priority(Priority.HIGH).skipMemoryCache(true).format(DecodeFormat.PREFER_RGB_565);
        RequestManager build = Glide.with(iv.getContext());
        if(imageUrl.endsWith(".gif")){
            build.asGif();
        }
        build.load(imageUrl).apply(options).transition(withCrossFade(400))
                .listener(new MyGlideLibModule.LoggingListener()).into(new DrawableThumbnailImageViewTarget(iv));
    }

    @Override
    public void loadFullScreenImage(final ImageView iv, String imageUrl, int width, final LinearLayout bgLinearLayout) {
        RequestOptions options = new RequestOptions()
                .fitCenter()
                .placeholder(R.drawable.img_bg)
                .priority(Priority.HIGH).skipMemoryCache(true);
        RequestManager build = Glide.with(iv.getContext());
        if(imageUrl.endsWith(".gif")){
            build.asGif();
        }
        build.load(imageUrl).apply(options).transition(withCrossFade(400))
                .listener(new MyGlideLibModule.LoggingListener()).into(new DrawableImageViewTarget(iv));
    }

    public void loadCommonImg(Context context, String url, ImageView iv) {
        RequestOptions options = new RequestOptions()
                .fitCenter()
                .placeholder(R.drawable.vpn_trans_default);
        Glide.with(context)
                .load(url)
                .apply(options)
                .transition(withCrossFade())
                .into(iv);
    }
}
