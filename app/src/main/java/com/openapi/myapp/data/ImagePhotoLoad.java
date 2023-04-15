package com.openapi.myapp.data;

import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.etiennelawlor.imagegallery.library.adapters.FullScreenImageGalleryAdapter;
import com.etiennelawlor.imagegallery.library.adapters.ImageGalleryAdapter;
import com.openapi.ks.free1.R;
import com.openapi.myapp.adapter.IndexRecommendAdapter;
import com.openapi.myapp.bean.vo.RecommendVo;
import com.openapi.myapp.constant.BaseRes;
import com.openapi.myapp.data.showtype.ShowTypeContext;
import com.romainpiel.shimmer.Shimmer;
import com.openapi.common.util.MyGlideLibModule;
import com.openapi.common.util.StringUtils;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Created by dengt on 14-03-12.
 */
public class ImagePhotoLoad implements ImageGalleryAdapter.ImageThumbnailLoader, FullScreenImageGalleryAdapter.FullScreenImageLoader {
    public static RequestOptions options = new RequestOptions().fitCenter().skipMemoryCache(true).placeholder(R.drawable.img_bg)
            .priority(Priority.HIGH).format(DecodeFormat.PREFER_RGB_565);;
    public ImagePhotoLoad(Context context) {
    }
    public static RequestManager getBuilder(Context context,String url){

        RequestManager build = Glide.with(context);
        if(url.contains(".gif")){
            build.asGif();
        }
        return build;
    }
    public static void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Shimmer shimmer, boolean needShimmer, Context context) {
        ShowTypeContext.loadPhoto(holder,vo,shimmer,needShimmer,context);
    }

    public static void getCountryImage(Context context, ImageView iv, String url) {
        if (iv == null || !StringUtils.hasText(url)) {
            return;
        }

        String imgPath = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf("."));
        if(BaseRes.img.containsKey(imgPath)){
            iv.setImageResource(BaseRes.img.get(imgPath));
        }else{
            RequestOptions options = new RequestOptions()
                    .placeholder(R.drawable.img_bg)
                    .priority(Priority.HIGH).fitCenter();
            Glide.with(context)
                    .load(url).apply(options)
                    .into(iv);
        }

    }

    @Override
    public void loadImageThumbnail(ImageView iv, String imageUrl, int dimension) {
//        imageThumbnailLoader.loadImageThumbnail(iv, imageUrl, dimension);
        RequestManager build=getBuilder(iv.getContext(),imageUrl);
        build.load(imageUrl).apply(options).transition(withCrossFade(400))
                .listener(new MyGlideLibModule.LoggingListener()).into(new DrawableImageViewTarget(iv));
    }

    @Override
    public void loadFullScreenImage(final ImageView iv, String imageUrl, int width, final LinearLayout bgLinearLayout) {
        RequestManager build=getBuilder(iv.getContext(),imageUrl);
        build.load(imageUrl).apply(options).transition(withCrossFade(400))
                .listener(new MyGlideLibModule.LoggingListener()).into(new DrawableImageViewTarget(iv));
    }

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
}
