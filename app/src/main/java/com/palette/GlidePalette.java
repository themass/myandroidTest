package com.palette;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.openapi.commons.common.util.LogUtil;


/*
 * ©2017 Rajneesh Singh
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class GlidePalette<ModelType, TranscodeType> extends BitmapPalette implements RequestListener<TranscodeType> {

    protected RequestListener<TranscodeType> callback;

    protected GlidePalette() {
    }

    public static GlidePalette with(String url) {
        GlidePalette glidePalette = new GlidePalette();
        glidePalette.url = url;
        return glidePalette;
    }

    public GlidePalette use(@Profile int paletteProfile) {
        super.use(paletteProfile);
        return this;
    }

    public GlidePalette<ModelType, TranscodeType> intoBackground(View view) {
        return this.intoBackground(view, Swatch.RGB);
    }

    @Override
    public GlidePalette<ModelType, TranscodeType> intoBackground(View view, @Swatch int paletteSwatch) {
        super.intoBackground(view, paletteSwatch);
        return this;
    }

    public GlidePalette<ModelType, TranscodeType> intoTextColor(TextView textView) {
        return this.intoTextColor(textView, Swatch.TITLE_TEXT_COLOR);
    }

    @Override
    public GlidePalette<ModelType, TranscodeType> intoTextColor(TextView textView, @Swatch int paletteSwatch) {
        super.intoTextColor(textView, paletteSwatch);
        return this;
    }

    @Override
    public GlidePalette<ModelType, TranscodeType> crossfade(boolean crossfade) {
        super.crossfade(crossfade);
        return this;
    }

    @Override
    public GlidePalette<ModelType, TranscodeType> crossfade(boolean crossfade, int crossfadeSpeed) {
        super.crossfade(crossfade, crossfadeSpeed);
        return this;
    }

    public GlidePalette<ModelType, TranscodeType> setGlideListener(RequestListener<TranscodeType> listener) {
        this.callback = listener;
        return this;
    }

    @Override
    public GlidePalette<ModelType, TranscodeType> intoCallBack(CallBack callBack) {
        super.intoCallBack(callBack);
        return this;
    }

    @Override
    public GlidePalette<ModelType, TranscodeType> setPaletteBuilderInterceptor(PaletteBuilderInterceptor interceptor) {
        super.setPaletteBuilderInterceptor(interceptor);
        return this;
    }

    @Override
    public GlidePalette<ModelType, TranscodeType> skipPaletteCache(boolean skipCache) {
        super.skipPaletteCache(skipCache);
        return this;
    }

    @Override
    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<TranscodeType> target, boolean isFirstResource) {
        LogUtil.i("onLoadFailed - " + model+ "--- " + e.toString());
        return this.callback != null && this.callback.onLoadFailed(e, model, target, isFirstResource);
    }

    @Override
    public boolean onResourceReady(TranscodeType resource, Object model, Target<TranscodeType> target, DataSource dataSource, boolean isFirstResource) {

        LogUtil.i("onResourceReady - " + model+ "--- " + resource.toString());
        Bitmap b = null;
        if (resource instanceof Bitmap) {
            b = (Bitmap) resource;
        } else if (target instanceof BitmapHolder) {
            b = ((BitmapHolder) target).getBitmap();
        }

        if (b != null) {
            start(b);
        }

        return true;
    }


    public interface BitmapHolder {
        @Nullable
        Bitmap getBitmap();
    }

}