//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.openapi.ks.myapp.ui.sound;

import android.content.Intent;
import android.os.Bundle;

import androidx.viewpager2.widget.ViewPager2;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.openapi.core.util.ToastUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.chat.databinding.LayoutImgBigPageViewBinding;
import com.openapi.ks.myapp.adapter.FullScreenImagePagerAdapter;
import com.openapi.ks.myapp.task.SaveImageCallBack;
import com.openapi.ks.myapp.task.SaveImageTask;
import com.openapi.ks.myapp.ui.base.app.BaseSingleActivity;

import java.util.ArrayList;
import java.util.List;


public class MyFullScreenImageGalleryActivity extends BaseSingleActivity implements SaveImageCallBack {
    public static final String KEY_IMAGES = "KEY_IMAGES";
    public static final String KEY_POSITION = "KEY_POSITION";
    ViewPager2 viewPager;
    TextView tvPage;
    private List<String> images;
    private int position;
    private final ViewPager2.OnPageChangeCallback viewPagerOnPageChangeListener = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            if (MyFullScreenImageGalleryActivity.this.viewPager != null) {
                MyFullScreenImageGalleryActivity.this.setPosition(position);
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = this.getIntent();
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                this.images = extras.getStringArrayList(KEY_IMAGES);
                this.position = extras.getInt(KEY_POSITION);
            }
        }
        this.setContentView(R.layout.layout_img_big_page_view);
    }

    @Override
    protected void onBindContent(ViewGroup container) {
        super.onBindContent(container);
        LayoutImgBigPageViewBinding b = LayoutImgBigPageViewBinding.bind(contentRoot(container));
        viewPager = b.vpBigContainer;
        tvPage = b.tvPage;
        b.ivSave.setOnClickListener(this::onSave);
    }

    @Override
    protected void unbindViews() {
        viewPager = null;
        tvPage = null;
        super.unbindViews();
    }

    public void onSave(View view) {
        if (images == null || images.isEmpty() || viewPager.getAdapter() == null) {
            ToastUtil.showShort(R.string.save_fail);
            return;
        }
        ToastUtil.showShort(R.string.save_start);
        SaveImageTask.startSave(MyFullScreenImageGalleryActivity.this,
                images.get(viewPager.getCurrentItem()), MyFullScreenImageGalleryActivity.this);
    }

    @Override
    public void setupView() {
        if (images == null || images.isEmpty()) {
            ToastUtil.showShort(R.string.image_preview_failed);
            finish();
            return;
        }
        if (position < 0 || position >= images.size()) {
            position = 0;
        }
        viewPager.setAdapter(new FullScreenImagePagerAdapter(new FullScreenImagePagerAdapter.UrlSource() {
            @Override
            public int count() {
                return images.size();
            }

            @Override
            public String urlAt(int position) {
                return images.get(position);
            }
        }));
        viewPager.registerOnPageChangeCallback(viewPagerOnPageChangeListener);
        viewPager.setCurrentItem(position, false);
        setPosition(position);
        showToolbar(false);
    }

    public void onDestroy() {
        this.removeListeners();
        super.onDestroy();
    }

    private void setPosition(int position) {
        if (viewPager != null && viewPager.getAdapter() != null
                && images != null && images.size() > 1) {
            int totalPages = viewPager.getAdapter().getItemCount();
            tvPage.setVisibility(View.VISIBLE);
            tvPage.setText(String.format("%s / %s", position + 1, totalPages));
        } else if (tvPage != null) {
            tvPage.setVisibility(View.GONE);
        }
    }

    private void removeListeners() {
        if (this.viewPager != null) {
            this.viewPager.unregisterOnPageChangeCallback(this.viewPagerOnPageChangeListener);
        }
    }

    public void onSuccess() {
        ToastUtil.showShort(R.string.save_ok);
    }

    public void onFailed() {
        ToastUtil.showShort(R.string.save_fail);
    }

    @Override
    public boolean needShow() {
        return false;
    }
}
