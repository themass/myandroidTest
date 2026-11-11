package com.openapi.feature.content.common;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;
import static com.luck.picture.lib.thread.PictureThreadUtils.runOnUiThread;

import android.content.Context;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.DrawableImageViewTarget;
import com.openapi.common.util.log.LogUtil;
import com.openapi.network.glide.GlideLoggingListener;
import com.openapi.core.image.ImagePhotoLoad;
// 使用现代 Shimmer 替代
// import com.romainpiel.shimmer.Shimmer;
import com.openapi.feature.content.common.IndexRecommendAdapter;
import com.openapi.core.model.RecommendVo;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by openapi on 2017/11/30.
 */

public abstract class ShowTypeHandle {
    public abstract void loadPhoto(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Object shimmer, boolean needShimmer, Context context);

    public abstract void showTitle(final IndexRecommendAdapter.NaviItemViewHolder holder, RecommendVo vo, final Object shimmer, boolean needShimmer, Context context);
    // 定义获取Base64字符串的方法
    public void fetchBase64ImageFromUrl(String url, IndexRecommendAdapter.NaviItemViewHolder holder, Context context) {
        LogUtil.i("img url ="+url);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                LogUtil.e("onFailure",e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    // 获取文件内容（Base64字符串）
                    String base64String = response.body().string();
                    LogUtil.i("base64String",base64String);
                    // 切换到主线程加载图片
                    runOnUiThread(
                            () -> {
                                RequestManager build = Glide.with(context);
                                ;
                                build.load(base64String).apply(ImagePhotoLoad.options).transition(withCrossFade(400))
                                        .listener(new GlideLoggingListener()).into(new DrawableImageViewTarget(holder.ivPhoto));
                            }
                    );
                }
            }
        });
    }
}
