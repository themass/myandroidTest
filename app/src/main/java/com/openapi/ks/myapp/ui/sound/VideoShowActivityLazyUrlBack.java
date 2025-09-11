//package com.openapi.ks.myapp.ui.sound;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import android.widget.ImageView;
//import android.widget.Toast; // 使用 Android 原生 Toast
//
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.google.android.exoplayer2.ExoPlayer;
//// 如果你的 JZVD 使用的是自定义的 ExoPlayer 实例，并且可以获取到，
//// 你可能需要通过 JZVD 的 MediaPlayerInterface 来获取
//
//
//import com.openapi.ks.chat.R;
//import com.openapi.ks.myapp.bean.vo.RecommendVo;
//import com.openapi.ks.myapp.constant.Constants;
//import com.openapi.ks.myapp.data.ImagePhotoLoad;
//import com.openapi.ks.myapp.data.VideoUtil;
//import com.openapi.ks.myapp.ui.sound.media.MyJzvdStd; // 你的自定义 JzvdStd
//// import com.openapi.ks.myapp.utils.JzvdPlayerFactory; //假设你有这个工厂类
//
//import com.openai.commonevent.FavoriteVo;
//import com.openai.commonutil.LogUtil;
//import com.openai.commonutil.StringUtils;
//import com.openai.commonutil.ToastUtil; // 你项目中的 Toast 工具类
//import com.openai.commonview.view.MyFavoriteView;
//import com.openai.net.BaseService;
//import com.openai.net.request.CommonResponse;
//
//
//// --- 火山 ASR SDK 相关导入 (请替换为实际的火山 SDK 包名和类名) ---
//// 确保这些导入路径与你项目中火山SDK的实际路径一致
//// --- 结束 火山 ASR SDK 相关导入 ---
//
//import java.nio.ByteBuffer;
//import java.util.UUID;
//
//
//import butterknife.BindView;
//import butterknife.ButterKnife;
//import butterknife.Unbinder;
//import cn.jzvd.Jzvd;
//import cn.jzvd.JzvdStd;
//
//
//public class VideoShowActivityLazyUrlBack extends AppCompatActivity implements MyFavoriteView.OnFavoriteItemClick {
//
//    private static final String VIDEO_TAG = "video_url_tag";
//    private static final String ASR_TAG = "VolcASR";
//    private Unbinder unbinder;
//
//    @BindView(R.id.jz_video)
//    public MyJzvdStd jzVideo;
//    RecommendVo vo;
//    @BindView(R.id.my_favoriteview)
//    MyFavoriteView myFavoriteView;
//
//    protected BaseService indexService;
//    private String currentVideoUrl; // 用于存储最终播放的 URL
//
//    // --- 火山 ASR 相关成员变量 ---
//    private SpeechRecognizer speechRecognizer;private boolean isAsrStreaming = false;
//    private final Handler mainHandler = new Handler(Looper.getMainLooper());
//    private ExoPlayer exoPlayerInstance; // 用于持有ExoPlayer实例
//
//    // 火山 ASR 配置 (请根据火山文档和你的账户信息填写)
//    // ** 重要: 请务必替换为你自己的真实配置信息 **
//    private final String VOLC_APP_ID = "YOUR_VOLC_APP_ID";
//    private final String VOLC_TOKEN = "YOUR_VOLC_TOKEN"; // 注意Token的有效期和动态获取机制
//    private final String VOLC_ASR_URL = "wss://openspeech.bytedance.com/api/v2/asr"; // 示例URL，请查阅火山文档确认
//    private final String VOLC_CLUSTER = "volcengine_streaming_asr"; // 示例集群，请查阅火山文档确认
//
//    // 音频参数，目标是火山ASR的要求
//    private static final int ASR_TARGET_SAMPLE_RATE = 16000; // 火山ASR通常要求16kHz
//    private static final int ASR_TARGET_CHANNELS = 1;       // 单声道
//    // private static final com.google.android.exoplayer2.C.PcmEncoding ASR_TARGET_ENCODING = com.google.android.exoplayer2.C.ENCODING_PCM_16BIT; // ExoPlayer的PCM编码表示
//    // --- 结束 火山 ASR 相关成员变量 ---
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.layout_video_show);
//        unbinder = ButterKnife.bind(this);
//
//        indexService = new BaseService();
//        indexService.setup(this);
//
//        vo = (RecommendVo) getIntent().getSerializableExtra(Constants.CONFIG_PARAM);
//        if (vo == null) {
//            ToastUtil.showShort("Video data is missing.");
//            finish();
//            return;
//        }
//        currentVideoUrl = vo.actionUrl; // 初始 URL
//        LogUtil.i("Initial data=" + vo);
//
//        // 设置播放器封面
//        jzVideo.posterImageView.setScaleType(ImageView.ScaleType.CENTER_CROP); // 或者 FIT_CENTER
//        ImagePhotoLoad.loadCommonImg(this, vo