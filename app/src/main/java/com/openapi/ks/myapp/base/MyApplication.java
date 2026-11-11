package com.openapi.ks.myapp.base;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.openapi.core.CommonConstants;
import com.openapi.core.media.AudioPlayer;
import com.openapi.common.util.android.StaticDataUtil;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.common.util.android.ScreenSize;
import com.openapi.common.util.android.FileUtils;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.SystemUtils;
import com.openapi.core.ads.yewu.ads.base.AdsManager;
import com.openapi.common.net.http.HttpClient;
import com.openapi.common.net.http.BaseService;
import com.openapi.chat.ChatDependencies;
import com.openapi.core.config.Constants;
import com.openapi.chat.data.remote.BackendSpeechToText;
import com.openapi.chat.data.remote.ChatRepositoryImpl;
import com.openapi.core.db.AppDatabase;
import com.openapi.ks.myapp.translation.VideoTranslationInstaller;
import com.openapi.ks.myapp.video.PlaybackEngineHelper;
import com.openapi.ks.myapp.data.ImagePhotoLoad;
import com.openapi.ks.myapp.data.VersionUpdater;
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
        LogUtil.e("isDebug=" + isDebug);
        instance = this;
        // 网络层的业务配置（域名、请求头名、登录失效处理）在启动时注入，commonutil 不再写死
        com.openapi.common.net.http.NetConfig.init(
                CommonConstants.DEFAULT_REFERER,
                CommonConstants.HTTP_TOKEN_KEY,
                CommonConstants.DEVID,
                code -> com.openapi.core.event.CoreEvents.INSTANCE.postUserNeedLogin());
        com.openapi.common.ui.widget.MyWebView.setDefaultReferer(CommonConstants.DEFAULT_REFERER);
        // 拍照解题：配置了服务地址才换成真实实现（见 app/build.gradle 的 photosolveBaseUrl），否则保留 mock
        if (!com.openapi.ks.chat.BuildConfig.PHOTOSOLVE_BASE_URL.isEmpty()) {
            com.aimath.paizhao.photosolve.data.PhotoSolveRepositories.INSTANCE.install(
                    new com.aimath.paizhao.photosolve.data.HttpPhotoSolveRepository(com.openapi.ks.chat.BuildConfig.PHOTOSOLVE_BASE_URL, new okhttp3.OkHttpClient.Builder().build()));
        }
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
        ChatDependencies.INSTANCE.install(new ChatRepositoryImpl(this));
        ChatDependencies.INSTANCE.installLoginGate(new com.openapi.ks.myapp.data.chat.LegacyLoginGate());
        com.openapi.ks.myapp.data.home.LegacyHomeEntries home = new com.openapi.ks.myapp.data.home.LegacyHomeEntries(this);
        com.openapi.home.HomeDependencies.INSTANCE.install(home);
        com.openapi.home.ChannelsDependencies.INSTANCE.install(home);
        com.openapi.home.ProfileDependencies.INSTANCE.install(home);
        // 以前由旧首页页面注册，页面不在前台就收不到；现在随进程注册一次
        com.openapi.ks.myapp.content.config.ConfigActionJump.start();
        AppEventHandler.INSTANCE.register();
        com.openapi.roles.data.RolesDependencies.INSTANCE.install(new com.openapi.ks.myapp.data.roles.RolesRepositoryImpl(this));
        BaseService speechService = new BaseService();
        speechService.setup(this);
        ChatDependencies.INSTANCE.installSpeechToText(new BackendSpeechToText(speechService));
        com.openapi.core.util.FavoriteUtil.install(new com.openapi.core.db.AppFavoriteStore());
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
     * {@link com.openapi.ks.chatfree.ui.main.MainFragmentViewPage} 会在进入主页后按设置再次应用。
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
