//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.qq.myapp.ui.base;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;
import android.widget.TextView;

import com.qq.common.util.ToastUtil;
import com.qq.fq2.R;
import com.qq.myapp.adapter.MyFullScreenImageGalleryAdapter;
import com.qq.myapp.base.MyApplication;
import com.qq.myapp.task.SaveImageCallBack;
import com.qq.myapp.task.SaveImageTask;
import com.qq.myapp.ui.base.app.BaseSingleActivity;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;

public class MyFullScreenImageGalleryActivity extends BaseSingleActivity implements SaveImageCallBack {
    public static final String KEY_IMAGES = "KEY_IMAGES";
    public static final String KEY_POSITION = "KEY_POSITION";
    @BindView(R.id.vp_big_container)
    ViewPager viewPager;
    @BindView(R.id.tv_page)
    TextView tvPage;
    private List<String> images;
    private int position;
    private final OnPageChangeListener viewPagerOnPageChangeListener = new OnPageChangeListener() {
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        public void onPageSelected(int position) {
            if (MyFullScreenImageGalleryActivity.this.viewPager != null) {
                MyFullScreenImageGalleryActivity.this.viewPager.setCurrentItem(position);
                MyFullScreenImageGalleryActivity.this.setPosition(position);
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
                this.position = extras.getInt(KEY_POSITION);
            }
        }
        this.setContentView(R.layout.layout_img_big_page_view);
    }
    @OnClick(R.id.iv_save)
    public void onSave(View view){
        ToastUtil.showShort(R.string.save_start);
        SaveImageTask.startSave(MyFullScreenImageGalleryActivity.this,images.get(viewPager.getCurrentItem()),MyFullScreenImageGalleryActivity.this);
    }
    @Override
    public void setupView() {
        ArrayList imageList = new ArrayList();
        imageList.addAll(this.images);
        MyFullScreenImageGalleryAdapter fullScreenImageGalleryAdapter = new MyFullScreenImageGalleryAdapter(imageList);
        fullScreenImageGalleryAdapter.setFullScreenImageLoader(MyApplication.getInstance().getPhotoLoad());
        this.viewPager.setAdapter(fullScreenImageGalleryAdapter);
        this.viewPager.addOnPageChangeListener(this.viewPagerOnPageChangeListener);
        this.viewPager.setCurrentItem(this.position);
        this.setPosition(this.position);
        showToolbar(false);
    }

    public void onDestroy() {
        this.removeListeners();
        super.onDestroy();
    }

    private void setPosition(int position) {
        if (this.viewPager != null && this.images.size() > 1) {
            int totalPages = this.viewPager.getAdapter().getCount();
            tvPage.setText(String.format("%s/%s", String.valueOf(position + 1), String.valueOf(totalPages)));
        }

    }

    private void removeListeners() {
        this.viewPager.removeOnPageChangeListener(this.viewPagerOnPageChangeListener);
    }
    public void onSuccess(){
        ToastUtil.showShort(R.string.save_ok);
    }

    public void onFailed(){
        ToastUtil.showShort(R.string.save_fail);
}
    @Override
    public boolean needShow() {
        return false;
    }
}
