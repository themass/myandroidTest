package com.openapi.ks.myapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.openapi.ks.chat.R;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewPager 全屏图片查看适配器（PhotoView + Glide）。
 */
public class MyFullScreenImageGalleryAdapter extends PagerAdapter {

    private final List<String> images;
    private final LayoutInflater inflater;

    public MyFullScreenImageGalleryAdapter(Context context, List<String> images) {
        this.images = images != null ? new ArrayList<>(images) : new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return images.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        View view = inflater.inflate(R.layout.item_modern_image_gallery, container, false);
        PhotoView photoView = view.findViewById(R.id.photo_view);
        Glide.with(photoView.getContext())
                .load(normalizeImageUrl(images.get(position)))
                .placeholder(R.drawable.img_bg)
                .error(R.drawable.img_bg)
                .into(photoView);
        container.addView(view);
        return view;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }

    public static String normalizeImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("file://") || url.startsWith("content://")) {
            return url;
        }
        if (url.startsWith("/")) {
            return "file://" + url;
        }
        return "file:///" + url;
    }
}
