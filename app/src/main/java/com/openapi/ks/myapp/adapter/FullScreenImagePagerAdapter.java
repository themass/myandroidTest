package com.openapi.ks.myapp.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.openapi.ks.chat.R;
import com.openapi.ks.chat.databinding.ItemModernImageGalleryBinding;

/**
 * ViewPager2 上的全屏图片页（PhotoView + Glide）。
 *
 * 图片地址通过 [UrlSource] 按需读取，列表数据变化（比如上拉加载更多）后调用 notifyDataSetChanged 即可。
 */
public class FullScreenImagePagerAdapter extends RecyclerView.Adapter<FullScreenImagePagerAdapter.PageHolder> {

    public interface UrlSource {
        int count();

        String urlAt(int position);
    }

    private final UrlSource source;

    public FullScreenImagePagerAdapter(UrlSource source) {
        this.source = source;
    }

    @NonNull
    @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new PageHolder(inflater.inflate(R.layout.item_modern_image_gallery, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        Glide.with(holder.photoView.getContext())
                .load(normalizeImageUrl(source.urlAt(position)))
                .placeholder(R.drawable.img_bg)
                .error(R.drawable.img_bg)
                .into(holder.photoView);
    }

    @Override
    public int getItemCount() {
        return source.count();
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

    static final class PageHolder extends RecyclerView.ViewHolder {
        final PhotoView photoView;

        PageHolder(android.view.View itemView) {
            super(itemView);
            photoView = ItemModernImageGalleryBinding.bind(itemView).photoView;
        }
    }
}
