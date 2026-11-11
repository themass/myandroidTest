package com.openapi.ks.myapp.view;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.LogUtil;
import com.openapi.core.R;
import com.openapi.core.databinding.RippleBgLayoutBinding;
import com.skyfishjy.library.RippleBackground;

import java.util.ArrayList;

public class MyRadarScanView extends FrameLayout {
    private ImageView foundDevice1;
    private ImageView foundDevice2;

    public MyRadarScanView(@NonNull Context context) {
        super(context);
        setupView();
    }

    public MyRadarScanView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setupView();
    }

    public MyRadarScanView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView();
    }

    public MyRadarScanView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupView();
    }
    private void setupView() {
        LayoutInflater mInflater = LayoutInflater.from(getContext());
        View myView = mInflater.inflate(R.layout.ripple_bg_layout, null);
        addView(myView);
        RippleBgLayoutBinding b = RippleBgLayoutBinding.bind(myView);
        // 使用 RippleBackground 波纹效果
        RippleBackground rippleView = b.content;
        if (rippleView != null) {
            rippleView.startRippleAnimation();
        }
        LogUtil.i("start ripple");
        final Handler handler=new Handler(Looper.getMainLooper());
        foundDevice1=b.foundDevice1;
        foundDevice2=b.foundDevice2;
        ImageView button=b.centerImage;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                foundDevice();
            }
        },500);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                foundDeviceTwo();
            }
        },1500);
    }
    private void foundDevice(){
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(400);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        ArrayList<Animator> animatorList=new ArrayList<Animator>();
        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(foundDevice1, "ScaleX", 0f, 1.2f, 1f);
        animatorList.add(scaleXAnimator);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(foundDevice1, "ScaleY", 0f, 1.2f, 1f);
        animatorList.add(scaleYAnimator);
        animatorSet.playTogether(animatorList);
        foundDevice1.setVisibility(View.VISIBLE);
        animatorSet.start();
    }
    private void foundDeviceTwo(){
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(200);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        ArrayList<Animator> animatorList=new ArrayList<Animator>();
        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(foundDevice2, "ScaleX", 0f, 1.2f, 1f);
        animatorList.add(scaleXAnimator);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(foundDevice2, "ScaleY", 0f, 1.2f, 1f);
        animatorList.add(scaleYAnimator);
        animatorSet.playTogether(animatorList);
        foundDevice2.setVisibility(View.VISIBLE);
        animatorSet.start();
    }
}
