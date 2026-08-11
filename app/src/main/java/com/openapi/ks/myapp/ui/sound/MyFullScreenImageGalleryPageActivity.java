//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.openapi.ks.myapp.ui.sound;

import android.content.Intent;
import android.os.Bundle;

import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;

import android.view.View;
import android.widget.TextView;

import com.openai.commonutil.ToastUtil;
import com.openapi.ks.chat.R;
import com.openapi.ks.myapp.adapter.MyFullScreenImageGalleryAdapter;
import com.openapi.ks.myapp.task.SaveImageCallBack;
import com.openapi.ks.myapp.task.SaveImageTask;
import com.openapi.ks.myapp.ui.base.app.BaseSingleActivity;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;

public class MyFullScreenImageGalleryPageActivity extends BaseSingleActivity implements SaveImageCallBack {
    public static final String KEY_IMAGES = "KEY_IMAGES";
    public static final String KEY_IMAGES_REMOTE = "KEY_IMAGES_REMOTE";
    public static final String KEY_POSITION = "KEY_POSITION";
    @BindView(R.id.vp_big_container)
    ViewPager viewPager;
    @BindView(R.id.tv_page)
    TextView tvPage;
    private List<String> images;
    private List<String> imagesRemote;
    private int position;
    private final OnPageChangeListener viewPagerOnPageChangeListener = new OnPageChangeListener() {
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        public void onPageSelected(int position) {
            if (MyFullScreenImageGalleryPageActivity.this.viewPager != null) {
                MyFullScreenImageGalleryPageActivity.this.viewPager.setCurrentItem(position);
                MyFullScreenImageGalleryPageActivity.this.setPosition(position);
            }

        }

        public void onPageScrollStateChanged(int state) {
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = this.getIntent();
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                this.images = extras.getStringArrayList(KEY_IMAGES);
                this.imagesRemote = extras.getStringArrayList(KEY_IMAGES_REMOTE);
                this.position = extras.getInt(KEY_POSITION);
            }
        }
        this.setContentView(R.layout.layout_img_big_page_view);
    }

    @OnClick(R.id.iv_save)
    public void onSave(View view) {
        if (imagesRemote == null || imagesRemote.isEmpty() || viewPager.getAdapter() == null) {
            ToastUtil.showShort(R.string.save_fail);
            return;
        }
        ToastUtil.showShort(R.string.save_start);
        SaveImageTask.startSave(MyFullScreenImageGalleryPageActivity.this,
                imagesRemote.get(viewPager.getCurrentItem()), MyFullScreenImageGalleryPageActivity.this);
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
        viewPager.setAdapter(new MyFullScreenImageGalleryAdapter(this, images));
        viewPager.addOnPageChangeListener(viewPagerOnPageChangeListener);
        viewPager.setCurrentItem(position);
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
            int totalPages = viewPager.getAdapter().getCount();
            tvPage.setVisibility(View.VISIBLE);
            tvPage.setText(String.format("%s / %s", position + 1, totalPages));
        } else if (tvPage != null) {
            tvPage.setVisibility(View.GONE);
        }
    }

    private void removeListeners() {
        this.viewPager.removeOnPageChangeListener(this.viewPagerOnPageChangeListener);
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
