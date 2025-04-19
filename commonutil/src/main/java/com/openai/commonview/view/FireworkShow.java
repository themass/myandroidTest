package com.openai.commonview.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.animation.Animator;
import android.animation.ValueAnimator;

import androidx.annotation.NonNull;


import com.openai.commonutil.R;

import java.lang.ref.WeakReference;

public class FireworkShow extends SurfaceView implements SurfaceHolder.Callback {

    private DrawTask mDrawTask; //绘制UI线程
    private DrawView mLauncherView; //烟花爆炸的引信
    private DrawView mFirework; //爆炸的烟花
    private int mWidth; //控件宽度
    private int mHeight; //控件高度
    private Paint mPaint;
    private int top; //引信上升的位移
    private int lx, ly; //引信消失的位置
    private ValueAnimator mLauncherRiseAnimator; //引信上升效果
    private ValueAnimator mLauncherAlphaAnimator; //引信消失效果
    private ValueAnimator mBoomAnimator; //烟花爆炸效果
    private ValueAnimator mBoomEndAnimator; //烟花爆炸消失效果

    private boolean isDisappear = true; //烟花是否消失

    private static final int MSG_DRAW = 1;

    //获取Looper
    private static final HandlerThread mHandlerThread = new HandlerThread(FireworkShow.class.getName());
    static {
        mHandlerThread.start();
    }
    //Handler消息处理
    private final Handler.Callback mCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (message.what == MSG_DRAW) {
                mHandler.post(mDrawTask);
                mHandler.sendEmptyMessageDelayed(MSG_DRAW, 10);
            }
            return true;
        }
    };
    //用Looper创建弱引用Handler
    private final WeakHandler mHandler = new WeakHandler(mCallback, mHandlerThread.getLooper());
    private static class WeakHandler extends Handler {
        private final WeakReference<Callback> mWeakReference;
        public WeakHandler(Callback callback, Looper looper) {
            super(looper);
            mWeakReference = new WeakReference<>(callback);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            Callback mCallback = mWeakReference.get();
            if (mCallback != null) {
                mCallback.handleMessage(msg);
            }
        }
    }

    private MediaPlayer mediaPlayer;

    public FireworkShow(Context context) {
        this(context, null);
    }

    public FireworkShow(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }

    private void init() {
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setKeepScreenOn(true);
        holder.setFormat(PixelFormat.TRANSPARENT);

        mPaint = new Paint();
        mediaPlayer = MediaPlayer.create(getContext(), R.raw.cheer); // 假设欢呼声音频文件名为 cheers
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        if (mDrawTask == null) {
            mDrawTask = new DrawTask(surfaceHolder, this);
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int format, int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        mHandler.removeMessages(MSG_DRAW);
        mHandlerThread.quitSafely();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mLauncherView != null && mLauncherView.bitmap != null) {
            mLauncherView.bitmap.recycle();
            mLauncherView.bitmap = null;
        }
        if (mFirework != null && mFirework.bitmap != null) {
            mFirework.bitmap.recycle();
            mFirework.bitmap = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null) return;
        super.draw(canvas);
        //清空界面
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if (mLauncherView == null || mFirework == null) return;

        mLauncherView.top = mHeight - mLauncherView.bitmap.getHeight() - top;
        mLauncherView.left = (mWidth - mLauncherView.bitmap.getWidth()) / 2;
        mPaint.setAlpha(mLauncherView.alpha);
        canvas.drawBitmap(mLauncherView.bitmap, mLauncherView.left, mLauncherView.top, mPaint);

        canvas.save();
        mPaint.setAlpha(mFirework.alpha);
        mFirework.left = (int) (lx - mFirework.bitmap.getWidth() * mFirework.scale / 2.0f);
        mFirework.top = (int) (ly - mFirework.bitmap.getHeight() * mFirework.scale / 2.0f);
        canvas.scale(mFirework.scale, mFirework.scale, mFirework.left, mFirework.top);
        canvas.drawBitmap(mFirework.bitmap, mFirework.left, mFirework.top, mPaint);
        canvas.restore();
    }

    public void startFireworkShow() {
        mHandler.sendEmptyMessage(MSG_DRAW);
        initDrawView();
        //开始动画效果
        mLauncherRiseAnimator.start();
    }

    public void stopFireworkShow() {
        mHandler.removeMessages(MSG_DRAW);
        mLauncherRiseAnimator.cancel();
        mLauncherAlphaAnimator.cancel();
        mBoomAnimator.cancel();
        mBoomEndAnimator.cancel();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }


    private void initDrawView() {
        mLauncherView = new DrawView();
        mLauncherView.bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.anim_mic);

        mFirework = new DrawView();
        mFirework.bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_add);

        mLauncherRiseAnimator = ValueAnimator.ofInt(0, 1400);
        mLauncherRiseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mLauncherRiseAnimator.setDuration(4000);
        mLauncherRiseAnimator.addUpdateListener(valueAnimator -> top = (int) valueAnimator.getAnimatedValue());
        mLauncherRiseAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                isDisappear = false;
                //烟花不显示
                mFirework.alpha = 0;
                //引信发射
                mLauncherView.alpha = 255;
                mLauncherAlphaAnimator.start();
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                //记录引信消失的位置
                lx = mLauncherView.left;
                ly = mLauncherView.top;
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });

        mLauncherAlphaAnimator = ValueAnimator.ofInt(255, 50);
        mLauncherAlphaAnimator.setInterpolator(new LinearInterpolator());
        mLauncherAlphaAnimator.setDuration(4000);
        mLauncherAlphaAnimator.addUpdateListener(valueAnimator -> mLauncherView.alpha = (int) valueAnimator.getAnimatedValue());
        mLauncherAlphaAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mLauncherView.alpha = 0; //引信消失
                //烟花显示
                mBoomAnimator.start();
                if (mediaPlayer != null) {
                    mediaPlayer.start();
                }
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });

        mBoomAnimator = ValueAnimator.ofFloat(0.0f, 2.0f);
        mBoomAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mBoomAnimator.setDuration(2000);
        mBoomAnimator.setStartDelay(1000);
        mBoomAnimator.addUpdateListener(valueAnimator -> mFirework.scale = (float) valueAnimator.getAnimatedValue());
        mBoomAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                //烟花消失效果
                mBoomEndAnimator.start();
            }

            @Override
            public void onAnimationEnd(Animator animator) {

            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });

        mBoomEndAnimator = ValueAnimator.ofInt(255, 0);
        mBoomEndAnimator.setInterpolator(new DecelerateInterpolator());
        mBoomEndAnimator.setDuration(7000);
        mBoomEndAnimator.addUpdateListener(valueAnimator -> mFirework.alpha = (int) valueAnimator.getAnimatedValue());
        mBoomEndAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                isDisappear = true;
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isDisappear) {
            mLauncherRiseAnimator.cancel();
            initDrawView();
            mLauncherRiseAnimator.start();
        }
        return true;
    }

    //绘制元素
    private static class DrawView {
        int top;
        int left;
        Bitmap bitmap;
        float scale;
        int alpha;
    }


    //绘制子线程
    private static class DrawTask implements Runnable {
        private final SurfaceHolder holder;
        private final FireworkShow fireworkShow;

        public DrawTask(SurfaceHolder holder, FireworkShow fireworkShow) {
            this.holder = holder;
            this.fireworkShow = fireworkShow;
        }

        @Override
        public void run() {
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                synchronized (holder) {
                    fireworkShow.draw(canvas);
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }

        }
    }
}