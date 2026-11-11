package com.openapi.ks.myapp.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
// 使用现代图片查看器替代
// import com.etiennelawlor.imagegallery.library.adapters.FullScreenImageGalleryAdapter;
// import com.etiennelawlor.imagegallery.library.adapters.ImageGalleryAdapter;
import com.openapi.common.util.android.FileUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.common.net.netglide.MyGlideModule;
// 使用现代 Shimmer 替代
// import com.romainpiel.shimmer.Shimmer;
import com.openapi.core.R;
import com.openapi.core.model.RecommendVo;
import com.openapi.core.config.BaseRes;
import com.openapi.core.config.Constants;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by gqli on 2017/2/6.
 */
// 使用现代图片查看器替代
public class ImagePhotoLoad {
    public static RequestOptions options = new RequestOptions().fitCenter().skipMemoryCache(true).placeholder(R.drawable.img_bg)
            .priority(Priority.NORMAL).format(DecodeFormat.PREFER_RGB_565);
    public static RequestOptions optionsHigh = new RequestOptions().fitCenter().skipMemoryCache(true).placeholder(R.drawable.img_bg)
            .priority(Priority.IMMEDIATE).format(DecodeFormat.PREFER_RGB_565);
    ;

    public ImagePhotoLoad(Context context) {
    }

    public static RequestManager getBuilder(Context context, String url) {

        RequestManager build = Glide.with(context);
        if (url.contains(".gif")) {
            build.asGif();
        }
        return build;
    }


    public static void getCountryImage(Context context, ImageView iv, String url) {
        if (iv == null || StringUtils.isEmpty(url)) {
            return;
        }
        if (url.startsWith(Constants.IMAGE_RES_PRE)) {
            url = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf("."));
            iv.setImageResource(BaseRes.img.get(url));
        } else {
            RequestOptions options = new RequestOptions()
                    .placeholder(R.drawable.ic_launcher)
                    .priority(Priority.HIGH);
            Glide.with(context)
                    .load(url).apply(options)
                    .into(iv);
        }
    }

    // 使用现代图片查看器替代
    // @Override
    // public void loadImageThumbnail(ImageView iv, String imageUrl, int dimension) {
    //     RequestManager build = getBuilder(iv.getContext(), imageUrl);
    //     build.load(imageUrl).apply(options).transition(withCrossFade(400))
    //             .listener(new MyGlideModule.LoggingListener()).into(new DrawableImageViewTarget(iv));
    // }

    // @Override
    // public void loadFullScreenImage(final ImageView iv, String imageUrl, int width, final LinearLayout bgLinearLayout) {
    //     RequestManager build = getBuilder(iv.getContext(), imageUrl);
    //     build.load(imageUrl).apply(optionsHigh).transition(withCrossFade(400))
    //             .listener(new MyGlideModule.LoggingListener()).into(new DrawableImageViewTarget(iv));
    // }

    public static void loadCommonImg(Context context, String url, ImageView iv) {
        RequestOptions options = new RequestOptions()
                .fitCenter()
                .placeholder(R.drawable.img_bg);
        Glide.with(context)
                .load(url)
                .apply(options)
                .transition(withCrossFade())
                .into(iv);
    }
    public static void loadCommonImg(Context context, Uri url, ImageView iv) {
        RequestOptions options = new RequestOptions()
                .fitCenter()
                .placeholder(R.drawable.img_bg);
        Glide.with(context)
                .load(url)
                .apply(options)
                .transition(withCrossFade())
                .into(iv);
    }
}
