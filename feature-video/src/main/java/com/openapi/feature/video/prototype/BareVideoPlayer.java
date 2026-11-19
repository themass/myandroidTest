package com.openapi.feature.video.prototype;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.openapi.feature.video.R;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

/**
 * 新播放页（{@link com.openapi.feature.video.prototype.NewVideoActivity}）专用播放器。
 * <p>
 * 与 {@link com.openapi.feature.video.player.DetailGSYVideoPlayer} 的区别只有一点：布局里没有任何 GSY 自带控件。
 * GSY 的 {@code GSYVideoControlView#init} 对每个 id 都做了空判，缺 id 不会崩，这里只保留
 * {@code surface_container}（渲染容器）和 {@code thumb}（海报层）。
 * <p>
 * 因此播放器只负责解码与画面，顶部标题、时间轴、按钮、字幕全部由新页面自绘，
 * 横屏也不会再克隆一份播放器（克隆正是旧页面退出全屏后标题错位的来源）。
 */
public class BareVideoPlayer extends StandardGSYVideoPlayer {

    public BareVideoPlayer(Context context) {
        super(context);
    }

    public BareVideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    public BareVideoPlayer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public int getLayoutId() {
        return R.layout.player_bare;
    }

    /**
     * 新页面自己绘制播放/暂停按钮，GSY 的中间按钮在这里不存在。
     * 父类会对 {@code mStartButton} 做 instanceof 判断，null 时自然跳过。
     */
    @Override
    protected void updateStartImage() {
        if (mStartButton == null) {
            return;
        }
        super.updateStartImage();
    }

    /**
     * GSY 的空白点击只负责显隐自带控件条；新页面用自己的 {@code ClickTarget} 处理点击，
     * 单点交给父类保持双击快进等手势语义不变。
     */
    @Override
    public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
        return super.onTouch(v, event);
    }
}
