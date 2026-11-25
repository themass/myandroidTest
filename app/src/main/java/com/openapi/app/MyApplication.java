package com.openapi.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.openapi.core.config.CommonConstants;
import com.openapi.speech.audio.AudioPlayer;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.common.util.android.ScreenSize;
import com.openapi.common.util.android.FileUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.common.ads.base.AdsManager;
import com.openapi.common.net.http.HttpClient;
import com.openapi.common.net.http.BaseService;
import com.openapi.feature.chat.ChatDependencies;
import com.openapi.core.config.Constants;
import com.openapi.feature.chat.data.remote.BackendSpeechToText;
import com.openapi.feature.chat.data.remote.ChatRepositoryImpl;
import com.openapi.feature.chat.data.remote.AgentBackedChatRepository;
import com.openapi.feature.chat.data.remote.AgentChatRemote;
import com.openapi.feature.chat.data.remote.LocalChatStore;
import com.openapi.core.db.AppDatabase;
import com.openapi.feature.video.subtitle.VideoTranslationInstaller;
import com.openapi.feature.video.PlaybackEngineHelper;
import com.openapi.core.image.ImagePhotoLoad;
import com.openapi.core.update.VersionUpdater;
import java.io.File;

/**
 * Created by openapi on 2016/3/1.
 */
public class MyApplication extends MultiDexApplication {
    //    private RefWatcher refWatcher;
    public static volatile boolean isDebug = true;
    private static MyApplication instance = null;
    private ImagePhotoLoad photoLoad;
    public static final String UPDATE_STATUS_ACTION = "com.openapi.ks.chat.action.UPDATE_STATUS";
    public static boolean isTemp = false;


    //    public static RefWatcher getRefWatcher(Context context) {
//        MyApplication application = (MyApplication) context.getApplicationContext();
//        return application.refWatcher;
//    }
    public static MyApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isDebug = SystemUtils.isApkDebugable(this);
        // 广告总开关随构建类型就位：AdsManager 的所有入口按它放行（debug = 不加载不展示）。
        com.openapi.common.ads.base.AdsGate.setDebugBuild(isDebug);
        // 插屏节律的启动锚点：进程起来就记一次，插屏在这之前不弹（AdMob「打开即插屏」）。
        com.openapi.common.ads.base.InterstitialCadence.noteStartup();
        LogUtil.e("isDebug=" + isDebug);
        instance = this;
        // 网络层的业务配置（域名、请求头名、登录失效处理）在启动时注入，commonutil 不再写死
        com.openapi.common.net.http.NetConfig.init(
                CommonConstants.DEFAULT_REFERER,
                CommonConstants.HTTP_TOKEN_KEY,
                CommonConstants.DEVID,
                code -> com.openapi.core.event.CoreEvents.INSTANCE.postUserNeedLogin());
        com.openapi.common.ui.widget.MyWebView.setDefaultReferer(CommonConstants.DEFAULT_REFERER);
        // 存储管理：登记各模块自己产生的目录（设置页「存储管理」展示的就是这张表）
        com.openapi.app.StorageRegistry.INSTANCE.install(this);
        // 拍照/相册/截图共用 agent 的独立视觉解题接口，不创建聊天会话。
        com.openapi.feature.photosolve.data.PhotoSolveRepositories.INSTANCE.install(
                new com.openapi.feature.photosolve.data.NetworkPhotoSolveRepository(
                        com.openapi.ks.myapp.companion.CompanionConfig.INSTANCE.getBaseUrl(),
                        () -> com.openapi.common.util.android.PreferenceUtils.getPrefString(
                                this, com.openapi.common.net.http.NetConfig.tokenHeader(), null)));
        com.openapi.core.app.AppContext.init(this);

        StaticDataUtil.add(CommonConstants.APPLICATION,instance);
        ToastUtil.mContext = instance;
        // 语音消息播放器必须先 init，否则 obtain() 返回 null，点了没声音、波纹也不动
        AudioPlayer.INSTANCE.init(this);

        long start = System.currentTimeMillis();
//        refWatcher = LeakCanary.install(this);
        VersionUpdater.init(this);
        HttpClient.init(isDebug, this);
        VideoTranslationInstaller.install(this);
        initFilePath();
        AppDatabase.init(this);
        com.openapi.retromusic.App.init(this);
        // 对话页现在走 Python companion-agent 的 /v1（见 AgentBackedChatRepository 的类注释）。
        // 旧的 Java 实现仍然建出来，只作为 TTS 与自定义人设的委托——这两项 /v1 上还没有接口，
        // 不是遗留代码，是这个迁移阶段刻意保留的。
        ChatRepositoryImpl legacyChat = new ChatRepositoryImpl(this);
        ChatDependencies.INSTANCE.install(new AgentBackedChatRepository(
                this,
                new AgentChatRemote(
                        this,
                        com.openapi.ks.myapp.companion.CompanionConfig.INSTANCE.getBaseUrl(),
                        () -> com.openapi.common.util.android.PreferenceUtils.getPrefString(
                                this, com.openapi.common.net.http.NetConfig.tokenHeader(), null),
                        // Java 调 Kotlin 构造器用不到默认参数，locale 必须显式给
                        () -> "zh-CN"),
                new LocalChatStore(this),
                legacyChat));
        ChatDependencies.INSTANCE.installLoginGate(new com.openapi.app.data.chat.LegacyLoginGate());
        // 对话页错误提示的文案资源实现（整改清单 P2-14b）：应用级 Context，随进程存活
        ChatDependencies.INSTANCE.installStrings(
                new com.openapi.feature.chat.presentation.ResourceChatStrings(this));
        // 陪伴页错误提示的文案资源实现（cleanup 项 2）：与上面同款——feature 不认识 :app，
        // 由这里注入。CompanionViewModel 的 strings 参数默认取它，漏装就是启动后第一次报错时炸。
        com.openapi.ks.myapp.companion.CompanionDependencies.INSTANCE.installStrings(
                new com.openapi.ks.myapp.companion.ResourceCompanionStrings(this));
        com.openapi.app.data.home.LegacyHomeEntries home = new com.openapi.app.data.home.LegacyHomeEntries(this);
        com.openapi.app.home.HomeDependencies.INSTANCE.install(home);
        com.openapi.feature.user.home.ProfileDependencies.INSTANCE.install(home);
        // 以前由旧首页页面注册，页面不在前台就收不到；现在随进程注册一次
        com.openapi.feature.content.config.ConfigActionJump.start();
        AppEventHandler.INSTANCE.register();
        com.openapi.feature.chat.roles.data.RolesDependencies.INSTANCE.install(new com.openapi.feature.chat.roles.data.RolesRepositoryImpl(this));
        BaseService speechService = new BaseService();
        speechService.setup(this);
        ChatDependencies.INSTANCE.installSpeechToText(new BackendSpeechToText(speechService));
        com.openapi.core.favorite.FavoriteUtil.install(new com.openapi.core.db.AppFavoriteStore());
        Constants.initUserAgent(Constants.AGENT_APP_MYPOOL);
        isTemp = false;
        //欺骗应用市场
//        if(Constants.APP_MYPOOL.equals(uc)){
//            Constants.initUserAgent(Constants.AGENT_APP_MYPOOL);
//        }else{
//            Constants.initUserAgent(Constants.AGENT_APP_GOOGLE);
//            isTemp = true;
//        }

        if (MyApplication.isDebug) {
            Constants.initUserAgent(Constants.AGENT_APP_MYPOOL);
            ScreenSize.logDensity(this);
            isTemp = false;
        }
        long cost = System.currentTimeMillis() - start;
        LogUtil.i("cpu=" + SystemUtils.getCpuType());
        LogUtil.e("app start cost:" + cost);
        photoLoad = new ImagePhotoLoad(this);
        AdsManager.getInstans().init(this);
        initVideoPlaybackDefaults();

//        CalligraphyConfig.initDefault(
//                new CalligraphyConfig.Builder()
//                        .setDefaultFontPath("fonts/Roboto-Monospace-Regular.ttf")
//                        .setFontAttrId(R.attr.fontPath)
//                        .build()
//        );
        ijkInit();
        initChat();
    }

    public ImagePhotoLoad getPhotoLoad() {
        return photoLoad;
    }

    private void initFilePath() {
        String tmpFilePath = FileUtils.getWriteFilePath(this) + File.separator + "log";
        LogUtil.i("tmpFilePath=" + tmpFilePath);
        FileUtils.ensureFile(this, tmpFilePath);

    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    private static SharedPreferences mPreferences, metaData, eqPref;

    public static SharedPreferences getmPreferences() {
        return mPreferences;
    }

    public static SharedPreferences getMetaData() {
        return metaData;
    }

    public static SharedPreferences getEqPref() {
        return eqPref;
    }

    /**
     * 与列表 GSY（Exo2 + Exo 缓存）及详情 JZ（默认 Exo）对齐的启动配置；
     * 首页会在进入后按设置再次应用。
     */
    private void initVideoPlaybackDefaults() {
        try {
            PlaybackEngineHelper.migrateLegacyIfNeeded(this);
            PlaybackEngineHelper.applyEngine(this);
        } catch (Throwable t) {
            LogUtil.e(t);
        }
    }

    private void ijkInit() {
        //EXOPlayer内核，支持格式更多
//        PlayerFactory.setPlayManager(Exo2PlayerManager.class);
//        //系统内核模式
//        PlayerFactory.setPlayManager(SystemPlayerManager.class);
        //ijk内核，默认模式
//        PlayerFactory.setPlayManager(IjkPlayerManager.class);


        //exo缓存模式，支持m3u8，只支持exo
//        CacheFactory.setCacheManager(ExoPlayerCacheManager.class);
        //代理缓存模式，支持所有模式，不支持m3u8等，默认
//        CacheFactory.setCacheManager(ProxyCacheManager.class);
    }

    public void initChat() {

//        StorageService.unpack(this, "model-small-cn", "model",
//                (model) -> {
//                    MyApplication.modelCh = model;
//                },
//                (e) -> LogUtil.e("model-small-cn", e));
    }

}
