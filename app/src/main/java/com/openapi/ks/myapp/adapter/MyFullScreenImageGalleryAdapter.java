package com.openapi.ks.myapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.openapi.ks.chat.R;

import java.util.List;

/**
 * 现代全屏图片查看器适配器 - 替代 ImageGallery
 * 使用 PhotoView 实现图片缩放和查看功能
 */
public class MyFullScreenImageGalleryAdapter extends RecyclerView.Adapter<MyFullScreenImageGalleryAdapter.ImageViewHolder> {
    
    private List<String> mImages;
    private Context mContext;
    
    public MyFullScreenImageGalleryAdapter(List<String> images) {
        mImages = images;
    }
    
    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mContext = parent.getContext();
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_modern_image_gallery, parent, false);
        return new ImageViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imageUrl = mImages.get(position);
        
        // 使用 Glide 加载图片
        Glide.with(mContext)
                .load(imageUrl)
                .placeholder(R.drawable.img_bg)
                .error(R.drawable.img_bg)
                .into(holder.photoView);
    }
    
    @Override
    public int getItemCount() {
        return mImages != null ? mImages.size() : 0;
    }
    
    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        PhotoView photoView;
        
        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photo_view);
        }
    }
}