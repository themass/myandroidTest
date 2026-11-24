package com.openapi.common.ui.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.Scroller;

import com.openapi.common.R;


/**
 * Created by chenyan.wang on 2015/10/29.
 */
public class SlidingLayout extends FrameLayout {
    // 页面边缘阴影的宽度默认值
    private static final int SHADOW_WIDTH = 16;
    // B6'：拦截区/消费区统一为屏幕左缘 24dp（原来拦截用 10% 屏宽、消费用 250px，两套口径打架）
    public static final float EDGE_ZONE_DP = 24f;
    private Activity mActivity;
    private Scroller mScroller;
    // 页面边缘的阴影图
    private Drawable mLeftShadow;
    // 页面边缘阴影的宽度
    private int mShadowWidth;
    // B6'：左缘判定阈值（px），默认 24dp×density，setmTouchDownInterceptX 可覆盖
    private int mEdgeZonePx;
    // B6'：横向滑动的最小累计位移（px），取系统 touch slop（≈8dp×density），
    // 没有它轻点后的第一个 MOVE（1~2px）就会被当成横滑
    private float mTouchSlopPx;
    private int mInterceptDownX;
    private int mLastInterceptX;
    private int mLastInterceptY;
    // B6'：拦截判定用自 DOWN 起的累计位移（原来只看相邻两个 MOVE 的单增量，抖动即误判）
    private int mInterceptAccX;
    private int mInterceptAccY;
    private int mLastTouchX;
    private int mLastTouchY;
    private int mTouchAccX;
    private int mTouchAccY;
    private boolean isConsumed = false;
    private int mTouchDownX;

    public SlidingLayout(Context context) {
        this(context, null);
    }

    public SlidingLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        mScroller = new Scroller(context);
        mLeftShadow = getContext().getDrawable(R.drawable.left_shadow);
        float density = getResources().getDisplayMetrics().density;
        mShadowWidth = SHADOW_WIDTH * (int) density;
        // B6'：拦截区/消费区统一 24dp；最小滑动距离用系统 touch slop（≈8dp×density）
        mEdgeZonePx = Math.round(EDGE_ZONE_DP * density);
        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /**
     * B6'：左缘滑动返回的纯判定（无 Android 依赖，JVM 单测覆盖）。
     * 用自按下起的「累计」位移：横向累计位移须超过 minDistancePx（touch slop，
     * 过滤轻点后的手指抖动），且须明显大于纵向累计位移（×2，纵向为主视为列表滚动），
     * 且按下点在左缘拦截区内。
     * 原实现只看相邻两个 MOVE 的单增量、无 slop（1~2px 抖动即误判），
     * 且拦截区（10% 屏宽）与消费区（250px）口径不一。
     */
    static boolean isEdgeSwipeGesture(int downX, int edgeZonePx,
                                      int accumulatedDx, int accumulatedDy,
                                      float minDistancePx) {
        if (downX >= edgeZonePx) {
            return false;
        }
        int absX = Math.abs(accumulatedDx);
        int absY = Math.abs(accumulatedDy);
        return absX > minDistancePx && absX > absY * 2;
    }

    /**
     * 绑定Activity
     */
    public void bindActivity(Activity activity) {
        mActivity = activity;
        ViewGroup decorView = (ViewGroup) mActivity.getWindow().getDecorView();
        View child = decorView.getChildAt(0);
        decorView.removeView(child);
        addView(child);
        decorView.addView(this);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercept = false;
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                intercept = false;
                mInterceptDownX = x;
                mLastInterceptX = x;
                mLastInterceptY = y;
                mInterceptAccX = 0;
                mInterceptAccY = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                mInterceptAccX += x - mLastInterceptX;
                mInterceptAccY += y - mLastInterceptY;
                // 手指处于左缘拦截区，且累计横移明显大于纵移（超 touch slop）时拦截事件
                intercept = isEdgeSwipeGesture(mInterceptDownX, mEdgeZonePx,
                        mInterceptAccX, mInterceptAccY, mTouchSlopPx);
                mLastInterceptX = x;
                mLastInterceptY = y;
                break;
            case MotionEvent.ACTION_UP:
                intercept = false;
                mInterceptDownX = mLastInterceptX = mLastInterceptY = 0;
                mInterceptAccX = mInterceptAccY = 0;
                break;
        }
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownX = x;
                mLastTouchX = x;
                mLastTouchY = y;
                mTouchAccX = 0;
                mTouchAccY = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                mTouchAccX += x - mLastTouchX;
                mTouchAccY += y - mLastTouchY;
                // 消费区与拦截区同一口径（默认 24dp×density，Activity 可用 setmTouchDownInterceptX 覆盖）
                if (!isConsumed && isEdgeSwipeGesture(mTouchDownX, mEdgeZonePx,
                        mTouchAccX, mTouchAccY, mTouchSlopPx)) {
                    isConsumed = true;
                }

                if (isConsumed) {
                    int rightMovedX = mLastTouchX - (int) ev.getX();
                    // 左侧即将滑出屏幕
                    if (getScrollX() + rightMovedX >= 0) {
                        scrollTo(0, 0);
                    } else {
                        scrollBy(rightMovedX, 0);
                    }
                }
                mLastTouchX = x;
                mLastTouchY = y;
                break;
            case MotionEvent.ACTION_UP:
                isConsumed = false;
                mTouchDownX = mLastTouchX = mLastTouchY = 0;
                mTouchAccX = mTouchAccY = 0;
                // 根据手指释放时的位置决定回弹还是关闭
                if (-getScrollX() < getWidth() / 2) {
                    scrollBack();
                } else {
                    scrollClose();
                }
                break;
        }
        return true;
    }

    /**
     * 滑动返回
     */
    private void scrollBack() {
        int startX = getScrollX();
        int dx = -getScrollX();
        mScroller.startScroll(startX, 0, dx, 0, 300);
        invalidate();
    }

    /**
     * 滑动关闭
     */
    private void scrollClose() {
        int startX = getScrollX();
        int dx = -getScrollX() - getWidth();
        mScroller.startScroll(startX, 0, dx, 0, 300);
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), 0);
            postInvalidate();
        } else if (-getScrollX() >= getWidth()) {
            mActivity.finish();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawShadow(canvas);
    }

    /**
     * 绘制边缘的阴影
     */
    private void drawShadow(Canvas canvas) {
        mLeftShadow.setBounds(0, 0, mShadowWidth, getHeight());
        canvas.save();
        canvas.translate(-mShadowWidth, 0);
        mLeftShadow.draw(canvas);
        canvas.restore();
    }

    /** 当前生效的左缘判定区（px）。 */
    public int getmTouchDownInterceptX() {
        return mEdgeZonePx;
    }

    /** 覆盖左缘判定区（px）；≤0 忽略，保持默认 24dp×density。拦截与消费同一口径。 */
    public void setmTouchDownInterceptX(int mTouchDownInterceptX) {
        if (mTouchDownInterceptX > 0) {
            this.mEdgeZonePx = mTouchDownInterceptX;
        }
    }
}
