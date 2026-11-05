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
import com.openapi.core.util.FileUtils;
import com.openapi.core.util.LogUtil;
import com.openapi.core.util.StringUtils;
import com.openapi.core.net.netglide.MyGlideModule;
import com.openapi.ks.myapp.task.DelImageTask;
// 使用现代 Shimmer 替代
// import com.romainpiel.shimmer.Shimmer;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.adapter.IndexRecommendAdapter;
import com.openapi.ks.myapp.bean.vo.RecommendVo;
import com.openapi.ks.myapp.constant.BaseRes;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.showtype.ShowTypeContext;

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

    public static void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Object shimmer, boolean needShimmer, Context context) {
        ShowTypeContext.loadPhoto(holder, vo, shimmer, needShimmer, context);
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
    /**
     * 转换图片并在完成后通过回调通知
     * @param context 上下文
     * @param imagePath 原始图片路径
     * @param callback 处理完成后的回调
     */
    private static String WEBP = "webp";
    public static void covertImage(Context context, String imagePath, ImageConversionCallback callback) {
        try {
            int lastIndex = imagePath.lastIndexOf('/');
            if (lastIndex == -1) {
                lastIndex = 0;
            } else {
                lastIndex++;
            }
            String allFileName = imagePath.substring(lastIndex);
            String fileShartName = allFileName.split("\\.")[0];
            String fileExt = allFileName.replace(fileShartName,"");
            boolean isWebp = fileExt.toLowerCase().contains(WEBP);
            LogUtil.i("covertImage isWebp:" + isWebp);
            if(isWebp){
                if (callback != null) {
                    callback.onConversionSuccess(new File(imagePath));
                }
                return;
            }
            String fileName = fileShartName + "."+WEBP;
            LogUtil.i("covertImage fileName:" + fileName + " , orgFileName:" + imagePath);

            Glide.with(context)
                    .asBitmap()
                    .load(imagePath)
                    .apply(new RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true))
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            try {
                                int width = resource.getWidth();
                                int height = resource.getHeight();
                                int newWidth = width / 2;
                                int newHeight = height / 2;
                                Bitmap scaledBitmap = Bitmap.createScaledBitmap(resource, newWidth, newHeight, true);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 80, baos);
                                byte[] byteArray = baos.toByteArray();
                                // 保存文件
                                File outputFile = new File(FileUtils.getWriteFilePath(context), fileName);
                                String outputPath = outputFile.getAbsolutePath();
                                LogUtil.i("保存文件：" + outputPath);
                                FileOutputStream fos = new FileOutputStream(outputFile);
                                fos.write(byteArray);
                                fos.close();
//                                DelImageTask.startDel(context, outputPath);
                                // 处理成功，调用回调
                                if (callback != null) {
                                    callback.onConversionSuccess(outputFile);
                                }
                            } catch (Exception e) {
                                LogUtil.e("Error in covertImage onResourceReady: ", e);
                                // 处理失败，调用回调
                                if (callback != null) {
                                    callback.onConversionSuccess(new File(imagePath));
                                }
                            } finally {
                                // 释放资源
                                if (resource != null && !resource.isRecycled()) {
                                    resource.recycle();
                                }
                            }
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            super.onLoadFailed(errorDrawable);
                            LogUtil.e("Failed to load image: " + imagePath);
                            if (callback != null) {
                                callback.onConversionFailed(new Exception("Failed to load image"));
                            }
                        }
                    });

        } catch (Exception e) {
            LogUtil.e("Error in covertImage: ", e);
            // 处理失败，调用回调
            if (callback != null) {
                callback.onConversionFailed(e);
            }
        }
    }

    /**
     * 图片转换回调接口
     */
    public interface ImageConversionCallback {
        /**
         * 转换成功
         * @param outputFilePath 输出文件路径
         */
        void onConversionSuccess(File outputFilePath);

        /**
         * 转换失败
         * @param e 异常
         */
        void onConversionFailed(Exception e);
    }
}
