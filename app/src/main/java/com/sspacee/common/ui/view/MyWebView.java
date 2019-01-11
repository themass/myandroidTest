package com.sspacee.common.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.webkit.WebView;

import com.qq.myapp.constant.Constants;

import java.util.HashMap;
import java.util.Map;

public class MyWebView extends WebView {
    //手指向右滑动时的最小速度
    private static final int XSPEED_MIN = 200;
    //手指向右滑动时的最小距离
    private static final int XDISTANCE_MIN = 70;
    Context mContext;
    //记录手指按下时的横坐标。
    private float xDown;
    //用于计算手指滑动的速度。
    private VelocityTracker mVelocityTracker;
    private OnTouchRightSlide listener;

    public MyWebView(Context context) {
        super(context);
        this.mContext = context;
    }

    public MyWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
    }

    public MyWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.mContext = context;
    }

    public void setListener(OnTouchRightSlide listener) {
        this.listener = listener;
    }

    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        if (additionalHttpHeaders == null) {
            additionalHttpHeaders = new HashMap<>();
        }
        String ref = Constants.DEFAULT_REFERER+","+url;
        if (additionalHttpHeaders.get(Constants.REFERER) != null)
            ref = additionalHttpHeaders.get(Constants.REFERER) + "," + Constants.DEFAULT_REFERER;
        additionalHttpHeaders.put(Constants.REFERER, ref);
        super.loadUrl(url, additionalHttpHeaders);
    }

    @Override
    public void loadUrl(String url) {
        this.loadUrl(url, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        LogUtil.i("onTouchEvent mywebview");
        createVelocityTracker(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                xDown = event.getRawX();
                break;
            case MotionEvent.ACTION_UP:
                float xMove = event.getRawX();
                //活动的距离
                int distanceX = (int) (xMove - xDown);
                //获取顺时速度
//                int xSpeed = getScrollVelocity();
                //当滑动的距离大于我们设定的最小距离且滑动的瞬间速度大于我们设定的速度时，返回到上一个activity
                // if(distanceX > XDISTANCE_MIN && xSpeed > XSPEED_MIN) {
                if (listener != null) {
                    listener.onTouchRight(distanceX);
                }
                recycleVelocityTracker();
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * 创建VelocityTracker对象，并将触摸content界面的滑动事件加入到VelocityTracker当中。
     *
     * @param event
     */
    private void createVelocityTracker(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    /**
     * 回收VelocityTracker对象。
     */
    private void recycleVelocityTracker() {
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    /**
     * 获取手指在content界面滑动的速度。
     *
     * @return 滑动速度，以每秒钟移动了多少像素值为单位。
     */
    private int getScrollVelocity() {
        mVelocityTracker.computeCurrentVelocity(1000);
        int velocity = (int) mVelocityTracker.getXVelocity();
        return Math.abs(velocity);
    }

    public interface OnTouchRightSlide {
        void onTouchRight(int distans);
    }
}
