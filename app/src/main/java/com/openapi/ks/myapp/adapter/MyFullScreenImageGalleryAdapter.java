package com.openapi.ks.myapp.adapter;

import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.etiennelawlor.imagegallery.library.adapters.FullScreenImageGalleryAdapter;
import com.openapi.ks.moviefree1.R;

import java.util.List;

/**
 * Created by openapi on 2017/11/8.
 */

public class MyFullScreenImageGalleryAdapter extends FullScreenImageGalleryAdapter {
    public MyFullScreenImageGalleryAdapter(List<String> images) {
        super(images);
    }
    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        View view = (View)object;
        Glide.with(view).clear((View)view.findViewById(R.id.iv));
        super.destroyItem(container,position,object);

    }
}