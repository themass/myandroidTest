package com.timeline.vpn.ui.view;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.PopupWindow;

import com.timeline.vpn.R;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.common.util.SystemUtils;

/**
 * 提示框 自定义view，使用PopupWindow 实现，注意显示时机
 *
 * @author gqli
 */
public class HeartAnimView {
    private static final DecelerateInterpolator DECCELERATE_INTERPOLATOR = new DecelerateInterpolator();
    private static final AccelerateInterpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();
    public  static  void startAnim(View actionView, View bgView, final AnimEndListner listner) {
        bgView.setVisibility(View.VISIBLE);
        actionView.setVisibility(View.VISIBLE);

        bgView.setScaleY(0.1f);
        bgView.setScaleX(0.1f);
        bgView.setAlpha(1f);
        actionView.setScaleY(0.1f);
        actionView.setScaleX(0.1f);

        AnimatorSet animatorSet = new AnimatorSet();

        ObjectAnimator bgScaleYAnim = ObjectAnimator.ofFloat(bgView, "scaleY", 0.1f, 1f);
        bgScaleYAnim.setDuration(200);
        bgScaleYAnim.setInterpolator(DECCELERATE_INTERPOLATOR);
        ObjectAnimator bgScaleXAnim = ObjectAnimator.ofFloat(bgView, "scaleX", 0.1f, 1f);
        bgScaleXAnim.setDuration(200);
        bgScaleXAnim.setInterpolator(DECCELERATE_INTERPOLATOR);
        ObjectAnimator bgAlphaAnim = ObjectAnimator.ofFloat(bgView, "alpha", 1f, 0f);
        bgAlphaAnim.setDuration(200);
        bgAlphaAnim.setStartDelay(150);
        bgAlphaAnim.setInterpolator(DECCELERATE_INTERPOLATOR);

        ObjectAnimator imgScaleUpYAnim = ObjectAnimator.ofFloat(actionView, "scaleY", 0.1f, 1f);
        imgScaleUpYAnim.setDuration(300);
        imgScaleUpYAnim.setInterpolator(DECCELERATE_INTERPOLATOR);
        ObjectAnimator imgScaleUpXAnim = ObjectAnimator.ofFloat(actionView, "scaleX", 0.1f, 1f);
        imgScaleUpXAnim.setDuration(300);
        imgScaleUpXAnim.setInterpolator(DECCELERATE_INTERPOLATOR);

        ObjectAnimator imgScaleDownYAnim = ObjectAnimator.ofFloat(actionView, "scaleY", 1f, 0f);
        imgScaleDownYAnim.setDuration(300);
        imgScaleDownYAnim.setInterpolator(ACCELERATE_INTERPOLATOR);
        ObjectAnimator imgScaleDownXAnim = ObjectAnimator.ofFloat(actionView, "scaleX", 1f, 0f);
        imgScaleDownXAnim.setDuration(300);
        imgScaleDownXAnim.setInterpolator(ACCELERATE_INTERPOLATOR);

        animatorSet.playTogether(bgScaleYAnim, bgScaleXAnim, bgAlphaAnim, imgScaleUpYAnim, imgScaleUpXAnim);
        animatorSet.play(imgScaleDownYAnim).with(imgScaleDownXAnim).after(imgScaleUpYAnim);

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if(listner!=null)
                    listner.animEnd();
            }
        });
        animatorSet.start();
    }
    public static interface AnimEndListner{
        public void animEnd();
    }
    public static void show(final Activity context) {
        ViewGroup view = (ViewGroup) View.inflate(context, R.layout.anim_heart_view, null);
        View vBg =  view.findViewById(R.id.v_bglike);
        View vAction =  view.findViewById(R.id.iv_like);
        final PopupWindow pop = new PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pop.setBackgroundDrawable(new ColorDrawable(context.getResources().getColor(R.color.transparent)));
        pop.setFocusable(true);
        pop.setOutsideTouchable(true);
        int w = (SystemUtils.getDisplayWidth(context)-context.getResources().getDimensionPixelSize(R.dimen.heart_anim_h))/2;
        int h = (SystemUtils.getDisplayHeight(context)-context.getResources().getDimensionPixelSize(R.dimen.heart_anim_h))/2;
        LogUtil.i("fw ="+SystemUtils.getDisplayWidth(context)+"---"+context.getResources().getDimensionPixelSize(R.dimen.heart_anim_h));
        pop.showAtLocation(context.getWindow().getDecorView(), Gravity.LEFT | Gravity.TOP, w, h);
        startAnim(vBg,vAction,new AnimEndListner(){
            @Override
            public void animEnd() {
                pop.dismiss();
            }
        });
    }
}
