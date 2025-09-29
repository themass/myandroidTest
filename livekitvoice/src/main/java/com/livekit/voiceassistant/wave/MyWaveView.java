package com.livekit.voiceassistant.wave;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.aghajari.waveanimation.AXLineWaveView;
import com.aghajari.waveanimation.AXWaveView;
import com.livekit.voiceassistant.R;

/**
 * 自定义波纹视图 - 使用原始AXWaveAnimation库
 * 恢复原始的UI效果，保持与之前版本的一致性
 */
public class MyWaveView extends FrameLayout {
    private static final String TAG = "MyWaveView";
    
    // AXWaveAnimation组件
    private AXWaveView circleWave;
    private AXLineWaveView lineWave;
    private ImageView centerImage;
    
    // 音频振幅 (0-1) - 分别控制圆形和线性波纹
    private float agentAmplitude = 0f;  // Agent语音振幅，控制圆形波纹
    private float userAmplitude = 0f;   // 用户语音振幅，控制线性波纹
    private boolean isActive = false;
    private boolean showCircular = true;
    private boolean showLinear = true;  // 默认同时显示两种波纹
    
    public MyWaveView(Context context) {
        super(context);
        init();
    }
    
    public MyWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public MyWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 加载原始布局
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.wave_view, this, true);
        
        // 初始化AXWaveAnimation组件
        circleWave = findViewById(R.id.circle_wave);
        lineWave = findViewById(R.id.line_wave);
        centerImage = findViewById(R.id.center_image);
        
        // 设置背景透明
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
        
        Log.d(TAG, "MyWaveView initialized with AXWaveAnimation");
    }
    
    /**
     * 设置波纹类型
     */
    public void setWaveType(boolean showCircular, boolean showLinear) {
        this.showCircular = showCircular;
        this.showLinear = showLinear;
        
        // 立即更新可见性
        updateVisibility();
        
        Log.d(TAG, "Wave type set - Circular: " + showCircular + ", Linear: " + showLinear);
    }
    
    /**
     * 更新波纹可见性
     */
    private void updateVisibility() {
        if (circleWave != null) {
            circleWave.setVisibility(showCircular ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        
        if (lineWave != null) {
            lineWave.setVisibility(showLinear ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }
    
    /**
     * 开始波纹动画
     */
    public void startWaveAnimation() {
        if (isActive) {
            return;
        }
        
        isActive = true;
        
        // 根据设置的波纹类型显示相应的波纹
        updateVisibility();
        
        Log.d(TAG, "Wave animation started");
    }
    
    /**
     * 停止波纹动画
     */
    public void stopWaveAnimation() {
        isActive = false;
        
        // 隐藏所有波纹组件
        if (circleWave != null) {
            circleWave.setVisibility(android.view.View.GONE);
        }
        
        if (lineWave != null) {
            lineWave.setVisibility(android.view.View.GONE);
        }
        
        Log.d(TAG, "Wave animation stopped");
    }
    
    /**
     * 设置Agent音频振幅（控制圆形波纹）
     * @param agentAmplitude Agent振幅值 (0-1)
     */
    public void setAgentAmplitude(float agentAmplitude) {
        this.agentAmplitude = Math.max(0f, Math.min(1f, agentAmplitude));
        
        // 将振幅转换为AXWaveAnimation的振幅值，并大幅放大
        // AXWaveAnimation使用0-100的振幅值，这里放大到3000%让效果极其震撼
        int axAmplitude = (int) (this.agentAmplitude * 3000);
        
        // 设置圆形波纹振幅
        if (circleWave != null) {
            circleWave.setAmplitude(axAmplitude);
        }
        
        Log.d(TAG, "Agent amplitude set to: " + this.agentAmplitude + " (AX: " + axAmplitude + ")");
    }
    
    /**
     * 设置用户音频振幅（控制线性波纹）
     * @param userAmplitude 用户振幅值 (0-1)
     */
    public void setUserAmplitude(float userAmplitude) {
        this.userAmplitude = Math.max(0f, Math.min(1f, userAmplitude));
        
        // 将振幅转换为AXWaveAnimation的振幅值，并大幅放大
        // AXWaveAnimation使用0-100的振幅值，这里放大到3500%让用户波形效果极其震撼
        int axAmplitude = (int) (this.userAmplitude * 3500);
        
        // 设置线性波纹振幅
        if (lineWave != null) {
            lineWave.setAmplitude(axAmplitude);
        }
        
        Log.d(TAG, "User amplitude set to: " + this.userAmplitude + " (AX: " + axAmplitude + ")");
    }
    
    /**
     * 设置音频振幅（兼容旧接口）
     * @param amplitude 振幅值 (0-1)
     */
    public void setAmplitude(float amplitude) {
        // 兼容旧接口，同时设置两种波纹
        setAgentAmplitude(amplitude);
        setUserAmplitude(amplitude);
    }
    
    /**
     * 设置波纹颜色
     */
    public void setWaveColor(int color) {
        // AXWaveAnimation的颜色设置需要在XML中配置
        // 这里只能记录日志，实际颜色需要在布局文件中设置
        Log.d(TAG, "Wave color set to: " + Integer.toHexString(color));
    }
    
    /**
     * 设置涟漪颜色
     */
    public void setRippleColor(int color) {
        // AXWaveAnimation的颜色设置需要在XML中配置
        Log.d(TAG, "Ripple color set to: " + Integer.toHexString(color));
    }
    
    /**
     * 设置波纹数量
     */
    public void setWaveCount(int count) {
        // AXWaveAnimation的波纹数量是固定的，无法动态调整
        Log.d(TAG, "Wave count requested: " + count + " (AXWaveAnimation uses fixed count)");
    }
    
    /**
     * 获取当前Agent振幅
     */
    public float getAgentAmplitude() {
        return agentAmplitude;
    }
    
    /**
     * 获取当前用户振幅
     */
    public float getUserAmplitude() {
        return userAmplitude;
    }
    
    /**
     * 获取当前振幅（兼容旧接口）
     */
    public float getAmplitude() {
        return Math.max(agentAmplitude, userAmplitude);
    }
    
    /**
     * 检查是否活跃
     */
    public boolean isActive() {
        return isActive;
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopWaveAnimation();
    }
}