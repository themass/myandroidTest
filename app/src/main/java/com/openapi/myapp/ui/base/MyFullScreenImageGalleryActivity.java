//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.openapi.myapp.ui.base;

import android.content.Intent;
import android.os.Bundle;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import android.view.View;
import android.widget.TextView;

import com.openapi.common.util.ToastUtil;
import com.openapi.ks.free1.R;
import com.openapi.myapp.adapter.MyFullScreenImageGalleryAdapter;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.task.SaveImageCallBack;
import com.openapi.myapp.task.SaveImageTask;
import com.openapi.myapp.ui.base.app.BaseSingleActivity;

import java.util.ArrayList;
import java.util.List;

public class MyFullScreenImageGalleryActivity extends BaseSingleActivity implements SaveImageCallBack {
    public static final String KEY_IMAGES = "KEY_IMAGES";
    public static final String KEY_POSITION = "KEY_POSITION";
    ViewPager viewPager;
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
        viewPager = findViewById(R.id.vp_big_container);
        tvPage = findViewById(R.id.tv_page);
        findViewById(R.id.iv_save).setOnClickListener(this::onSave);
    }
    private static final int REQ_SAVE_IMAGE = 3101;

    public void onSave(View view){
        // Android 9 及以下写相册要存储权限：在用户点"保存"这一刻再申请，不在启动时要
        if (SaveImageTask.needsLegacyPermission(this)) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_SAVE_IMAGE);
            return;
        }
        ToastUtil.showShort(R.string.save_start);
        SaveImageTask.startSave(MyFullScreenImageGalleryActivity.this,images.get(viewPager.getCurrentItem()),MyFullScreenImageGalleryActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_SAVE_IMAGE) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onSave(null);
        } else {
            onFailed();   // 拒绝就按保存失败提示，不反复弹
        }
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
