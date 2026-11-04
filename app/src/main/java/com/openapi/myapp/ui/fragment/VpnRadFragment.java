package com.openapi.myapp.ui.fragment;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.king.view.radarview.RadarView;
import com.openapi.common.ui.base.BaseFragment;
import com.openapi.common.util.DeviceInfoUtils;
import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.GsonUtils;
import com.openapi.common.util.IpUtil;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PermissionHelper;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.StringUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.data.local.ProtocolPreferences;
import com.openapi.data.vpn.CharonVpnSessionRepository;
import com.openapi.domain.model.SplitTunnelingPolicy;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.domain.model.VpnSessionState;
import com.openapi.domain.repository.TunnelRequest;
import com.openapi.domain.repository.VpnSessionRepository;
import com.openapi.myapp.base.AppContainer;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.bean.DataBuilder;
import androidx.core.content.ContextCompat;
import com.openapi.myapp.bean.vo.HostVo;
import com.openapi.myapp.bean.vo.InfoListVo;
import com.openapi.myapp.bean.vo.LocationVo;
import com.openapi.myapp.bean.vo.VipLocationVo;
import com.openapi.myapp.data.config.PingEvent;
import com.openapi.yewu.net.HttpUtils;
import com.openapi.myapp.ui.globe.GeoCatalog;
import com.openapi.myapp.ui.globe.GlobeWebView;
import com.openapi.myapp.ui.proton.ProtonHomeState;
import com.openapi.myapp.constant.BaseRes;
import com.openapi.myapp.ui.proton.ProtonVpnUi;
import androidx.compose.ui.platform.ComposeView;
import static com.openapi.myapp.ui.proton.ProtonHomeKt.bindProtonHome;
import com.openapi.myapp.bean.vo.IpAdress;
import com.openapi.myapp.bean.vo.ServerVo;
import com.openapi.myapp.bean.vo.VpnProfile;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.myapp.data.LocationUtil;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.yewu.ads.config.BannerAdsNext;
import com.openapi.myapp.data.config.IsChainEvent;
import com.openapi.ks.free1.R;
import com.openapi.ks.ui.main.MainFragmentViewPage;
import com.openapi.myapp.task.EmuTask;
import com.openapi.myapp.ui.base.NativeHeaderFragment;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.AdsManager;
import com.openapi.yewu.ads.mobvista.WallMobvAds;
import com.openapi.yewu.net.HttpUtils;
import com.openapi.yewu.net.request.CommonResponse;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.strongswan.android.logic.imc.ImcState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VPN 主界面。
 * <p>
 * 只负责渲染 {@link VpnSessionState} 并把用户操作翻译成仓库指令；
 * 连接状态、错误码、绑定/解绑全部由 {@link VpnSessionRepository} 承担。
 */
public class VpnRadFragment extends BaseFragment {
    private static final String DIALOG_TAG = "Dialog";
    private static final String INDEX_TAG = "vvv_status_tag";
    private static final String IPADRESS_TAG = "vvv_ip_get_tag";
    private static final String GLOBE_TAG = "vvv_globe_nodes_tag";

    private static final int PREPARE_VPN_SERVICE = 0;

    private BaseService indexService;
    private final AtomicBoolean connectingStarted = new AtomicBoolean(false);
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    LinearLayout rlContentNative;
    LinearLayout rlContent;
    RadarView vpn;
    pl.bclogic.pulsator4droid.library.PulsatorLayout vpnPulse;
    TextView tvVpnText;
    ImageButton ibVpnStatus;
    TextView tvEmText;
    TextView tvNotChain;
    RelativeLayout vpnBut;
    RelativeLayout rlGift;
    GlobeWebView globe;
    /** 全部线路节点（用于地球标注与测速）。 */
    private List<LocationVo> globeLocations = new ArrayList<>();
    /** 节点 id → 最近一次测得的延迟（毫秒，负数=超时）。 */
    private final Map<String, Integer> globePings = new ConcurrentHashMap<>();
    private boolean selectedPingRunning;
    LinearLayout cardCurrentNode;
    TextView tvCurrentName;
    TextView tvCurrentPing;
    android.widget.ImageButton btnGift;
    private android.animation.ObjectAnimator giftShake;
    android.widget.ImageView ivCurrentFlag;

    private PingTask task;
    private static boolean allPerm = false;
    private static boolean isEmulator = false;
    private final WallMobvAds wallMobvAds = new WallMobvAds();
    private VpnProfile vpnProfile;
    private ServerVo pendingServer;
    private HostVo pendingHost;

    private final CommonResponse.ResponseOkListener<String> ipAddressOk = new CommonResponse.ResponseOkListener<String>() {
        @Override
        public void onResponse(String str) {
            LogUtil.i("ipAddressOk-》" + str);
            try {
                if (!StringUtils.isEmpty(str)) {
                    IpAdress vo = GsonUtils.getInstance().fromJson(str, IpAdress.class);
                    if ("China".equals(vo.geoplugin_countryName) || "CN".equals(vo.geoplugin_countryCode)) {
                        onEvent(new IsChainEvent());
                    }
                }
            } catch (Exception e) {
                LogUtil.e("ipAddressOK", e);
            }
        }
    };

    private final CommonResponse.ResponseErrorListener ipAddressError = new CommonResponse.ResponseErrorListener() {
        @Override
        protected void onError() {
            super.onError();
        }
    };

    private final VpnCheckTask vpnCheck = new VpnCheckTask();

    private final CommonResponse.ResponseErrorListener serverListenerError = new CommonResponse.ResponseErrorListener() {
        @Override
        protected void onError() {
            super.onError();
            imgError();
            mHandler.removeCallbacks(vpnCheck);
        }
    };

    private final CommonResponse.ResponseOkListener<ServerVo> serverListener = new CommonResponse.ResponseOkListener<ServerVo>() {
        @Override
        public void onResponse(ServerVo serverVo) {
            if (serverVo.hostList == null) {
                return;
            }
            if (serverVo.hostList.size() == 1) {
                LogUtil.i("one host start ready");
                startVpn(serverVo, serverVo.hostList.get(0));
            } else {
                LogUtil.i("many host start task ping");
                for (HostVo vo : serverVo.hostList) {
                    LogUtil.i("run task " + vo.gateway);
                    task = new PingTask(serverVo);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, vo);
                }
            }
        }
    };

    private VpnSessionRepository repo;
    /** ProtonVPN 风格首页的状态；与旧版首页共用同一份连接状态。 */
    private final ProtonHomeState protonState = new ProtonHomeState();

    private static ProtonVpnUi toProtonUi(VpnSessionState state) {
        if (state.hasError()) {
            return ProtonVpnUi.Error;
        }
        if (state.isConnected()) {
            return ProtonVpnUi.Connected;
        }
        if (state.isConnecting()) {
            return ProtonVpnUi.Connecting;
        }
        return ProtonVpnUi.Idle;
    }

    @Override
    protected int getRootViewId() {
        if (isProtonHome()) {
            return R.layout.vpn_home_proton;
        }
        return isNewHome() ? R.layout.vpn_home_full : R.layout.vpn_rad_loading;
    }

    /** 节点选择：ProtonVPN 风格首页打开新列表页，否则保持原来的列表页。 */
    private void openServerList() {
        if (isProtonHome()) {
            com.openapi.myapp.ui.proton.ProtonServerListActivity.start(getActivity());
        } else {
            LocationPageViewFragment.startFragment(getActivity());
        }
    }

    /** ProtonVPN 风格首页开关（设置页切换，默认关）。 */
    private boolean isProtonHome() {
        return PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.PROTON_HOME, false);
    }

    /** 新版深色首页开关（设置页可切回旧版）。 */
    private boolean isNewHome() {
        return PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.NEW_HOME_LAYOUT, true);
    }

    public void onPoints(View v) {
//        if(getActivity() instanceof MainFragmentViewPage){
//            ((MainFragmentViewPage)getActivity()).showReward();
//        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = AppContainer.vpnSession();
        EventBusUtil.getEventBus().register(this);
    }

    @Override
    protected void bindViews(View view) {
        super.bindViews(view);
        rlContentNative = view.findViewById(R.id.rl_content_native);
        rlContent = view.findViewById(R.id.rl_content);
        vpn = view.findViewById(R.id.vpn);
        vpnPulse = view.findViewById(R.id.vpn_pulse);
        tvVpnText = view.findViewById(R.id.tv_vpn_state_text);
        ibVpnStatus = view.findViewById(R.id.iv_vpn_state);
        tvEmText = view.findViewById(R.id.rl_content_em);
        tvNotChain = view.findViewById(R.id.tv_not_chain);
        vpnBut = view.findViewById(R.id.vpn_but);
        rlGift = view.findViewById(R.id.rl_gift);
        globe = view.findViewById(R.id.globe);
        cardCurrentNode = view.findViewById(R.id.card_current_node);
        tvCurrentName = view.findViewById(R.id.tv_current_name);
        tvCurrentPing = view.findViewById(R.id.tv_current_ping);
        ivCurrentFlag = view.findViewById(R.id.iv_current_flag);
        btnGift = view.findViewById(R.id.btn_gift);
        if (btnGift != null) {
            btnGift.setOnClickListener(v -> showRewardAd());
        }
        view.findViewById(R.id.rl_points).setOnClickListener(this::onPoints);
        ibVpnStatus.setOnClickListener(this::onVpnClick);
    }

    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        indexService = new BaseService();
        indexService.setup(getActivity());
        if (DeviceInfoUtils.isEmulator(getActivity())) {
            ToastUtil.showShort(R.string.is_emulator);
            tvEmText.setVisibility(View.VISIBLE);
            isEmulator = true;
            EmuTask.start(getActivity());
        }
        wallMobvAds.load(getActivity(), rlGift);
        boolean newHome = PreferenceUtils.getPrefBoolean(getActivity(), Constants.NEW_HOME_LAYOUT, true);
        if (newHome) {
            view.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.pv_bg));
        }
        cardCurrentNode.setVisibility(newHome ? View.VISIBLE : View.GONE);
        cardCurrentNode.setOnClickListener(v -> openServerList());
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo.observeState().observe(getViewLifecycleOwner(), this::render);
        ComposeView proton = view.findViewById(R.id.proton_home);
        if (proton != null) {
            bindProtonHome(proton, protonState,
                    () -> onVpnClick(proton),
                    this::openServerList);
        }
    }

    /** 礼物图标：点击弹出激励视频，看完后由 AdmobRewardAds 上报积分。 */
    private void showRewardAd() {
        if (getActivity() != null) {
            com.openapi.myapp.ui.reward.RewardActivity.start(getActivity());
        }
    }

    /** 让礼物图标持续轻微晃动，吸引注意。 */
    private void startGiftShake() {
        if (btnGift == null) {
            return;
        }
        if (giftShake == null) {
            giftShake = android.animation.ObjectAnimator.ofFloat(btnGift, android.view.View.ROTATION,
                    0f, -14f, 14f, -10f, 10f, -4f, 0f);
            giftShake.setDuration(700);
            giftShake.setStartDelay(1500);
            giftShake.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            giftShake.setRepeatMode(android.animation.ObjectAnimator.RESTART);
        }
        if (!giftShake.isStarted()) {
            giftShake.start();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startGiftShake();
        // 选路可能在别的页面改变，回到首页时刷新地球上的目标节点与节点列表。
        setupGlobe();
        updateGlobeConnection(repo.current());
        refreshCurrentNode();
        offerReconnectIfSplitChanged();
        // 观看券 / VIP 即将到期提醒，每天最多一次
        com.openapi.myapp.ui.reward.ExpiryReminder.check(getActivity(), indexService);
    }

    /**
     * 分应用代理名单在别的页面改过、且当前正在连接时，询问是否重连使其生效。
     * 没有连接时只清掉标记，下次连接自然会用新名单。
     */
    private void offerReconnectIfSplitChanged() {
        if (!com.openapi.data.local.SplitTunnelingPreferences.consumeReconnectPending(getContext())) {
            return;
        }
        VpnSessionState current = repo.current();
        if (!current.isConnected() && !current.isConnecting()) {
            return;
        }
        new android.app.AlertDialog.Builder(getActivity())
                .setTitle(R.string.split_reconnect_title)
                .setMessage(R.string.split_reconnect_message)
                .setPositiveButton(R.string.split_reconnect_now, (d, w) -> reconnectNow())
                .setNegativeButton(R.string.split_reconnect_later, null)
                .show();
    }

    /** 先断开，等状态回到空闲，再用同一个节点重新连接（VPN 权限已在首次连接时授予）。 */
    private void reconnectNow() {
        if (pendingHost == null || pendingServer == null) {
            ToastUtil.showShort(R.string.vpn_bg_click_later);
            return;
        }
        repo.disconnect();
        waitIdleThenConnect(0);
    }

    private void waitIdleThenConnect(int tries) {
        if (!isAdded()) {
            return;
        }
        if (repo.current().isIdle()) {
            connectWithBestProtocol();
            return;
        }
        if (tries >= 20) {
            // 约 6 秒仍未断开：放弃自动重连，让用户手动再点一次。
            ToastUtil.showShort(R.string.vpn_bg_click_later);
            return;
        }
        mHandler.postDelayed(() -> waitIdleThenConnect(tries + 1), 300);
    }


    /** 当前节点卡片：显示已选线路的名称与最近一次测得的延迟；未选择时提示点击选择。 */
    private void refreshCurrentNode() {
        refreshCurrentNodeTexts();
        // ProtonVPN 风格首页读取同一份文字，保证两套首页显示一致。
        protonState.setServerName(tvCurrentName.getText().toString());
        protonState.setPingText(tvCurrentPing.getText().toString());
        Integer flag = currentFlagRes();
        protonState.setFlagRes(flag);
        if (ivCurrentFlag != null) {
            if (flag == null) {
                ivCurrentFlag.setVisibility(View.GONE);
            } else {
                ivCurrentFlag.setImageResource(flag);
                ivCurrentFlag.setVisibility(View.VISIBLE);
            }
        }
    }

    /** 当前选中节点的国旗：服务端 img 文件名去 BaseRes 映射表查找，找不到返回 null。 */
    private Integer currentFlagRes() {
        if (getActivity() == null) {
            return null;
        }
        LocationVo chosen = PreferenceUtils.getPrefObj(getActivity(), Constants.LOCATION_CHOOSE, LocationVo.class);
        if (chosen == null || chosen.img == null) {
            return null;
        }
        String name = chosen.img.substring(chosen.img.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return BaseRes.img.get(name);
    }

    private void refreshCurrentNodeTexts() {
        if (cardCurrentNode == null || cardCurrentNode.getVisibility() != View.VISIBLE || getActivity() == null) {
            return;
        }
        LocationVo chosen = PreferenceUtils.getPrefObj(getActivity(), Constants.LOCATION_CHOOSE, LocationVo.class);
        if (chosen == null) {
            tvCurrentName.setText(R.string.current_node_none);
            tvCurrentPing.setText("");
            return;
        }
        tvCurrentName.setText(chosen.ename != null ? chosen.ename : chosen.name);
        // 延迟优先用首页测速结果；没有测过时自动测一次选中的节点。
        Integer ms = globePings.get(String.valueOf(chosen.id));
        if (ms == null) {
            ms = chosen.ping;
        }
        if (ms == null) {
            tvCurrentPing.setText(R.string.ping_testing);
            tvCurrentPing.setTextColor(ContextCompat.getColor(getActivity(), R.color.pv_text_secondary));
            pingSelectedNode(chosen);
            return;
        }
        if (ms > 0) {
            tvCurrentPing.setText(ms + " ms");
            tvCurrentPing.setTextColor(ContextCompat.getColor(getActivity(), R.color.pv_green));
        } else {
            tvCurrentPing.setText(R.string.ping_timeout);
            tvCurrentPing.setTextColor(ContextCompat.getColor(getActivity(), R.color.pv_alert));
        }
    }

    /** 只测选中的节点，结果写入 globePings 并刷新卡片。 */
    private void pingSelectedNode(LocationVo chosen) {
        if (!StringUtils.hasText(chosen.gateway) || selectedPingRunning) {
            return;
        }
        selectedPingRunning = true;
        final String id = String.valueOf(chosen.id);
        final String gateway = chosen.gateway;
        new Thread(() -> {
            int ms = HttpUtils.pingVal(gateway);
            globePings.put(id, ms > 0 ? ms : -1);
            mHandler.post(() -> {
                selectedPingRunning = false;
                if (isAdded()) {
                    refreshCurrentNode();
                    pushGlobeNodes();
                }
            });
        }, "selected-ping").start();
    }

    /** 首页 3D 地球：按设置开关显示；用户位置用时区近似，节点取当前选中线路的类型。 */
    private void setupGlobe() {
        if (globe == null) {
            return;
        }
        boolean enabled = PreferenceUtils.getPrefBoolean(getActivity(), Constants.GLOBE_VIEW, true);
        globe.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            return;
        }
        double[] user = GeoCatalog.approximateUser();
        if (user != null) {
            globe.setUser(user[0], user[1], getString(R.string.globe_my_location));
        }
        // 网络体检检测过的出口位置：有才标记，没有就不画
        if (com.openapi.myapp.ui.check.ExitIpStore.hasExit(getActivity())) {
            globe.setExit(com.openapi.myapp.ui.check.ExitIpStore.getExitLat(getActivity()),
                    com.openapi.myapp.ui.check.ExitIpStore.getExitLng(getActivity()),
                    com.openapi.myapp.ui.check.ExitIpStore.getExitLabel(getActivity()));
        }
        loadGlobeNodes();
    }

    /** 节点与列表页同源：VIP 线路接口返回全部分类，展开后即所有节点。 */
    private void loadGlobeNodes() {
        final String url = Constants.getUrl(Constants.API_LOCATION_VIP_URL);
        new Thread(() -> {
            try {
                InfoListVo<VipLocationVo> data = indexService.getInfoListData(url, VipLocationVo.class, GLOBE_TAG);
                List<LocationVo> all = new ArrayList<>();
                if (data.voList != null) {
                    for (VipLocationVo group : data.voList) {
                        if (group.list != null) {
                            all.addAll(group.list);
                        }
                    }
                }
                mHandler.post(() -> {
                    if (isAdded()) {
                        globeLocations = all;
                        pushGlobeNodes();
                    }
                });
            } catch (Exception e) {
                LogUtil.e("3D 地球节点加载失败: " + e.getMessage());
            }
        }, "globe-nodes").start();
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /** 把节点推给地球：有坐标的才能画；测过速的带上延迟用于分级上色；选中的节点单独放大波纹。 */
    private void pushGlobeNodes() {
        if (globe == null || getActivity() == null) {
            return;
        }
        List<GlobeWebView.GeoNode> nodes = new ArrayList<>();
        for (LocationVo vo : globeLocations) {
            double[] ll = GeoCatalog.lookup(vo.cityName, vo.ename, vo.name);
            if (ll != null) {
                String id = String.valueOf(vo.id);
                // 地球标签只能显示 ASCII：中文节点名改用英文名（ename）
                // 显示 "城市, 国家"（英文）；识别不了时退回 ASCII 名或编号
                String display = GeoCatalog.displayName(vo.cityName, vo.ename, vo.name);
                String label = display != null ? display
                        : (vo.name != null && isAscii(vo.name) ? vo.name : "Node " + vo.id);
                nodes.add(new GlobeWebView.GeoNode(id, label, ll[0], ll[1], globePings.get(id)));
            }
        }
        LogUtil.i("3D 地球：节点 " + globeLocations.size() + " 个，有坐标 " + nodes.size() + " 个");
        for (LocationVo vo : globeLocations) {
            if (GeoCatalog.lookup(vo.cityName, vo.ename, vo.name) == null) {
                LogUtil.i("3D 地球：无坐标的节点 " + vo.ename + " / " + vo.name + " / " + vo.cityName);
            }
        }
        int selected = LocationUtil.getSelectLocationId(getActivity());
        globe.setNodes(nodes, selected > 0 ? String.valueOf(selected) : null);
    }

    /** 对所有节点并行测延迟（与列表页 Ping 按钮共用同一个 PingEvent），结束后重新上色。 */
    private void pingGlobeNodes() {
        final List<LocationVo> locs = new ArrayList<>(globeLocations);
        ToastUtil.showShort(getString(R.string.ping_testing_count, locs.size()));
        new Thread(() -> {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Callable<Void>> tasks = new ArrayList<>();
                for (LocationVo vo : locs) {
                    if (StringUtils.hasText(vo.gateway)) {
                        tasks.add(() -> {
                            // pingVal 超时/异常时返回 0，这里统一记为 -1（超时），否则会被当成最快的节点
                            int ms = HttpUtils.pingVal(vo.gateway);
                            globePings.put(String.valueOf(vo.id), ms > 0 ? ms : -1);
                            return null;
                        });
                    }
                }
                pool.invokeAll(tasks);
            } catch (Exception e) {
                LogUtil.e("3D 地球测速失败: " + e.getMessage());
            } finally {
                pool.shutdown();
            }
            mHandler.post(() -> {
                if (isAdded()) {
                    pushGlobeNodes();
                    refreshCurrentNode();
                    showPingSummary(locs);
                }
            });
        }, "globe-ping").start();
    }

    /** 测速结束的提示：可用 / 超时 / 地球上能标出的节点数，方便判断覆盖范围。 */
    private void showPingSummary(List<LocationVo> locs) {
        int ok = 0;
        int timeout = 0;
        for (LocationVo vo : locs) {
            Integer ms = globePings.get(String.valueOf(vo.id));
            if (ms == null) {
                continue;
            }
            if (ms > 0) {
                ok++;
            } else {
                timeout++;
            }
        }
        ToastUtil.showShort(getString(R.string.ping_summary, ok, timeout, locs.size()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PingEvent event) {
        if (globe == null || globe.getVisibility() != View.VISIBLE) {
            return;
        }
        if (globeLocations.isEmpty()) {
            ToastUtil.showShort(R.string.ping_servers_loading);
            loadGlobeNodes();
            return;
        }
        pingGlobeNodes();
    }

    /** 当前选中节点在地球上的 id；"最佳线路"(id<=0) 返回 null。 */
    private String globeTargetId() {
        int selected = LocationUtil.getSelectLocationId(getActivity());
        return selected > 0 ? String.valueOf(selected) : null;
    }

    /**
     * 地球上的连接线颜色：已连接绿、连接中黄、失败红（带 ✗）、未连接不画线。
     * 目标节点取当前选中的线路；"最佳线路"(id<=0) 无法对应到具体节点，不画线。
     */
    private void updateGlobeConnection(VpnSessionState state) {
        if (globe == null || state == null || globe.getVisibility() != View.VISIBLE || getActivity() == null) {
            return;
        }
        String target = globeTargetId();
        if (state.hasError()) {
            globe.setConnection("failed", target);
        } else if (state.isConnected()) {
            globe.setConnection("connected", target);
        } else if (state.isConnecting()) {
            globe.setConnection("connecting", target);
        } else {
            globe.setConnection("idle", null);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(IsChainEvent event) {
        if (UserLoginUtil.isVIP()) {
            return;
        }
        if (PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.IS_CHAIN, false)) {
            ToastUtil.showLong(R.string.support_Chinese);
        } else {
            String ip = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.USER_IP,
                    IpUtil.getInNetIp(getContext()));
            LogUtil.i("ip=" + ip);
            indexService.getStringData(Constants.IP_ADDRESS + ip, ipAddressOk, ipAddressError, IPADRESS_TAG);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BannerAdsNext next) {
        // 广告轮换预留：当前实现由 AdsManager 内部调度，这里无需处理。
    }

    public void showBanner() {
        AdsManager.getInstans().showBannerAds(getActivity(), rlContent, AdsContext.Categrey.CATEGREY_VPN, AdsContext.AdsFrom.ADMOB);
        FragmentManager fm = getChildFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.rl_content_native);
        if (fragment == null) {
            Fragment header = NativeHeaderFragment.getNewInstans(AdsContext.Categrey.CATEGREY_VPN);
            if (header == null) {
                rlContentNative.setVisibility(View.GONE);
            } else {
                fm.beginTransaction().replace(R.id.rl_content_native, header).commitAllowingStateLoss();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (giftShake != null) {
            giftShake.cancel();
        }

        indexService.cancelRequest(INDEX_TAG);
        if (task != null && !task.isCancelled()) {
            task.cancel(true);
        }
        mHandler.removeCallbacks(vpnCheck);
    }

    @Override
    public void onDestroy() {
        // 与 onCreate 中的 register 配对；放在 onDestroy 而非 onDestroyView，
        // 避免视图重建后收不到事件。
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroy();
    }

    public void onVpnClick(View v) {
        if (!allPerm) {
            if (!PermissionHelper.checkPermissions(getActivity()) && !UserLoginUtil.isVIP()) {
                PermissionHelper.showPermit(getActivity());
                return;
            }
            allPerm = true;
        }
        if (isEmulator && !UserLoginUtil.isVIP()) {
            ToastUtil.showShort(R.string.is_emulator);
            return;
        } else {
            tvEmText.setVisibility(View.GONE);
        }
        VpnSessionState current = repo.current();
        if (!current.isConnected()) {
            if (PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.CHECK_CHAIN, true)) {
                if (PreferenceUtils.getPrefBoolean(MyApplication.getInstance(), Constants.IS_CHAIN, false)
                        && !UserLoginUtil.isVIP()) {
                    ToastUtil.showLong(R.string.support_Chinese);
                    return;
                }
            }
        }
        toggleVpn();
    }

    private void toggleVpn() {
        VpnSessionState current = repo.current();
        LogUtil.i("toggleVpn " + current.getState());
        if (!current.isBound()) {
            // 仓库会自动重连；这里只把界面恢复到"未连接"。
            imgNormal();
            ToastUtil.showShort(R.string.vpn_bg_click_later);
            return;
        }
        if (current.isConnected() || current.isConnecting()) {
            // 连接中再点一下即取消，避免卡在“连接中”无法退出。
            repo.disconnect();
            return;
        }
        if (!current.isIdle()) {
            ToastUtil.showShort(R.string.vpn_bg_click_later);
            return;
        }
        mHandler.removeCallbacks(vpnCheck);
        mHandler.postDelayed(vpnCheck, Constants.VPN_CONNECT_TIMEOUT);
        imgAnim();
        // 地球立即进入“连接中”，不等 VPN 状态回调（那时才出现的线会让人以为没有连接效果）。
        if (globe != null && globe.getVisibility() == View.VISIBLE) {
            globe.setConnection("connecting", globeTargetId());
        }
        int id = LocationUtil.getSelectLocationId(getActivity());
        indexService.getData(String.format(Constants.getUrl(Constants.API_SERVERLIST_URL), id),
                serverListener, serverListenerError, INDEX_TAG, ServerVo.class);
    }

    /** 状态渲染的唯一入口；由 LiveData 在主线程回调。 */
    private void render(VpnSessionState state) {
        if (state == null) {
            return;
        }
        updateGlobeConnection(state);
        protonState.setVpnState(toProtonUi(state));
        if (state.hasError()) {
            mHandler.removeCallbacks(vpnCheck);
            imgError();
            showErrorToast(state.getErrorState(), state.getImcState());
            return;
        }
        switch (state.getState()) {
            case CONNECTED:
                mHandler.removeCallbacks(vpnCheck);
                imgConn();
                break;
            case CONNECTING:
                // 仓库层已经在做指数退避重连，这里只负责动画，不再重排看门狗——
                // 否则总超时会被每次 CONNECTING 回调不断往后推，变成"永远不超时"。
                imgAnim();
                break;
            case DISCONNECTING:
                mHandler.removeCallbacks(vpnCheck);
                break;
            case DISABLED:
                mHandler.removeCallbacks(vpnCheck);
                imgNormal();
                break;
            default:
                mHandler.removeCallbacks(vpnCheck);
                imgError();
                break;
        }
    }

    private void showErrorToast(org.strongswan.android.logic.VpnStateService.ErrorState errorState, ImcState imcState) {
        switch (errorState) {
            case AUTH_FAILED:
                if (imcState == ImcState.BLOCK) {
                    ToastUtil.showShort(R.string.error_assessment_failed);
                } else if (UserLoginUtil.getUserCache() == null) {
                    ToastUtil.showShort(R.string.error_auth_failed2);
                } else {
                    ToastUtil.showShort(R.string.error_auth_failed);
                }
                break;
            case PEER_AUTH_FAILED:
                ToastUtil.showShort(R.string.error_peer_auth_failed);
                break;
            case LOOKUP_FAILED:
                ToastUtil.showShort(R.string.error_lookup_failed);
                break;
            case UNREACHABLE:
                ToastUtil.showShort(R.string.error_unreachable);
                break;
            default:
                ToastUtil.showShort(R.string.error_generic);
                break;
        }
    }

    /**
     * Prepare the VpnService. If this succeeds the current VPN profile is
     * started.
     */
    protected void prepareVpnService() {
        Intent intent;
        try {
            intent = VpnService.prepare(getActivity());
        } catch (IllegalStateException ex) {
            /* this happens if the always-on VPN feature (Android 4.2+) is activated */
            VpnNotSupportedError.showWithMessage(getActivity(), R.string.vpn_not_supported_during_lockdown);
            return;
        }
        if (intent != null) {
            try {
                startActivityForResult(intent, PREPARE_VPN_SERVICE);
            } catch (ActivityNotFoundException ex) {
                VpnNotSupportedError.showWithMessage(getActivity(), R.string.vpn_not_supported);
            }
        } else {
            onActivityResult(PREPARE_VPN_SERVICE, Activity.RESULT_OK, null);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PREPARE_VPN_SERVICE) {
            if (resultCode == Activity.RESULT_OK) {
                connectWithBestProtocol();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void imgAnim() {
        if (isNewHome()) {
            // 新版首页不再使用雷达/波纹动画：按钮变黄色、文字显示连接中，并做透明度呼吸效果。
            // 连接中：按钮透明，原来的雷达扫描从按钮后面透出来。
            vpnBut.setVisibility(View.VISIBLE);
            ibVpnStatus.setBackgroundResource(R.drawable.pv_connect_connecting);
            tvVpnText.setText(R.string.vpn_bg_connecting_short);
            vpn.setVisibility(View.VISIBLE);
            vpn.start();
            return;
        }
        // 按设置选动画样式：默认原雷达扫描，打开"波纹动画"开关后用开源库 Pulsator4Droid（PulsatorLayout）。
        // 每次都重新读偏好，用户在设置页切换后回到首页无需重启就生效。
        boolean pulse = PreferenceUtils.getPrefBoolean(getContext(), Constants.PULSE_STYLE, true);
        if (pulse) {
            vpn.setVisibility(View.GONE);
            vpnPulse.setVisibility(View.VISIBLE);
            vpnPulse.start();
        } else {
            vpnPulse.stop();
            vpnPulse.setVisibility(View.GONE);
            vpn.setVisibility(View.VISIBLE);
            vpn.start();
        }
        vpnBut.setVisibility(View.GONE);
    }

    private void imgError() {
        stopAnim();
        ibVpnStatus.setBackgroundResource(isNewHome() ? R.drawable.pv_connect_error : R.drawable.vpn_ic_error);
        tvVpnText.setText(R.string.vpn_bg_error);
    }

    private void imgConn() {
        stopAnim();
        ibVpnStatus.setBackgroundResource(isNewHome() ? R.drawable.pv_connect_connected : R.drawable.vpn_ic_conn);
        tvVpnText.setText(R.string.vpn_bg_conned);
    }

    private void imgNormal() {
        stopAnim();
        ibVpnStatus.setBackgroundResource(isNewHome() ? R.drawable.pv_connect_button : R.drawable.vpn_state_bg);
        tvVpnText.setText(R.string.vpn_bg_click);
    }

    private void stopAnim() {
        vpnBut.setVisibility(View.VISIBLE);
        vpn.setVisibility(View.GONE);
        vpnPulse.stop();
        vpnPulse.setVisibility(View.GONE);
    }

    public void startVpn(ServerVo server, HostVo vo) {
        connectingStarted.set(true);
        vpnProfile = DataBuilder.builderVpnProfile(server.expire, server.name, server.pwd, vo);
        pendingServer = server;
        pendingHost = vo;
        prepareVpnService();
    }

    /**
     * 权限拿到后真正发起连接。
     * <p>
     * 节点声明的 protocols 决定它到底能走哪些协议——这不是"喜好"，是硬约束：
     * 一个只声明 vless-reality 的节点，物理上就没有 IKEv2 服务在监听，无论用户
     * 协议偏好是什么都不能往它身上发 IKEv2 包（会一直重传直到超时）。
     * 所以这里先判定节点"能走哪些协议"，再结合用户偏好决定实际用哪个，
     * 而不是反过来先看偏好、偏好不对就无脑走经典 IKEv2 路径。
     */
    private void connectWithBestProtocol() {
        ServerVo server = pendingServer;
        HostVo host = pendingHost;
        if (server == null || host == null) {
            return;
        }
        CharonVpnSessionRepository repo = (CharonVpnSessionRepository) this.repo;
        // 网络体检需要知道本次连接的节点地址，用来和出口 IP 对比
        com.openapi.myapp.ui.check.ExitIpStore.rememberNode(getContext(), host.gateway);
        TunnelProtocol preferred = ProtocolPreferences.getPreferred(getContext());
        SplitTunnelingPolicy splitPolicy =
                com.openapi.data.local.SplitTunnelingPreferences.getPolicy(getContext());

        boolean vlessReady = host.isVlessRealityReady();
        // 未声明 protocols 时 HostVo.supports() 按旧行为只认 IKEv2；
        // 一旦节点显式声明了 protocols（哪怕只写 vless-reality），这里才会是 false。
        boolean ikev2Supported = host.supports(com.openapi.domain.model.TunnelProtocol.IKEV2.getId());

        if (vlessReady) {
            TunnelRequest vlessRequest = new TunnelRequest.Builder()
                    .server(host.gateway)
                    .port(host.port)
                    .username(server.name)
                    .password(server.pwd)
                    .mtu(host.mtu)
                    .dnsServer(host.dns)
                    .splitTunneling(splitPolicy)
                    .vlessUuid(host.vlessUuid)
                    .vlessFlow(host.vlessFlow)
                    .realityServerName(host.realityServerName)
                    .realityPublicKey(host.realityPublicKey)
                    .realityShortId(host.realityShortId)
                    .realityFingerprint(host.realityFingerprint)
                    .build();

            // 用户显式选了 VLESS，或者这个节点压根不支持 IKEv2（没有退路可言）：
            // 两种情况都应该直接连 VLESS，不能把决定权交给"经典协议"那条分支。
            if (!ikev2Supported) {
                LogUtil.i("connectWithBestProtocol: 使用后端下发的 VLESS+Reality 节点 "
                        + host.gateway + ":" + host.port);
                repo.connect(TunnelProtocol.VLESS_REALITY, vlessRequest);
                return;
            }
        }

        if (!ikev2Supported) {
            // 走到这里说明：节点不支持 IKEv2，VLESS 也没配全（否则上面已经 return）。
            // 没有协议可用，绝不能像以前那样默默尝试 IKEv2——会表现成"连接中"卡住，
            // 用户完全看不出原因。这里从未建立过连接，调用 disconnect() 语义不对，
            // 直接提示用户比假装能连、或静默挂起更负责。
            LogUtil.e("connectWithBestProtocol: 节点 " + host.gateway
                    + " 不支持 IKEv2 且 VLESS 参数不全，没有可用协议");
            ToastUtil.showShort(R.string.error_unreachable);
            return;
        }

        TunnelRequest request = new TunnelRequest.Builder()
                .server(host.gateway)
                .port(host.port)
                .wireGuardPort(host.wireGuardPort)
                .username(server.name)
                .password(server.pwd)
                .certificate(host.cert)
                .mtu(host.mtu)
                .dnsServer(host.dns)
                .peerPublicKey(host.peerPublicKey)
                .clientAddress(host.clientAddress)
                .splitTunneling(splitPolicy)
                .build();

        // WireGuard 需要服务端公钥与客户端地址；缺失时不能选它。
        boolean wgReady = host.peerPublicKey != null && host.clientAddress != null;
        if (preferred == TunnelProtocol.WIREGUARD && !wgReady) {
            preferred = TunnelProtocol.IKEV2;
        }
        TunnelProtocol chosen = wgReady ? repo.connectAuto(request, preferred) : null;
        if (chosen == null) {
            // 没有任何协议可用，或 WireGuard 参数不全：退回既有的 IKEv2 路径。
            // 此处能安全这样做是因为上面已经确认 ikev2Supported == true。
            LogUtil.i("connectWithBestProtocol: 回退 IKEv2");
            repo.connect(vpnProfile);
        } else {
            LogUtil.i("connectWithBestProtocol: 采用 " + chosen);
        }
    }

    public static class VpnNotSupportedError extends DialogFragment {
        static final String ERROR_MESSAGE_ID = "org.strongswan.android.VpnNotSupportedError.MessageId";

        public static void showWithMessage(Activity activity, int messageId) {
            Bundle bundle = new Bundle();
            bundle.putInt(ERROR_MESSAGE_ID, messageId);
            VpnNotSupportedError dialog = new VpnNotSupportedError();
            dialog.setArguments(bundle);
            dialog.show(activity.getFragmentManager(), DIALOG_TAG);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle arguments = getArguments();
            final int messageId = arguments.getInt(ERROR_MESSAGE_ID);
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.vpn_not_supported_title)
                    .setMessage(messageId)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    }).create();
        }
    }

    public class PingTask extends AsyncTask<HostVo, Void, HostVo> {
        private final ServerVo server;

        public PingTask(ServerVo server) {
            this.server = server;
        }

        @Override
        protected HostVo doInBackground(HostVo... params) {
            HostVo vo = params[0];
            try {
                vo.ttlTime = HttpUtils.ping(vo.gateway);
                if (vo.ttlTime > 0
                        && repo.current().isIdle()
                        && connectingStarted.compareAndSet(false, true)) {
                    startVpn(server, vo);
                }
            } catch (Exception e) {
                LogUtil.e(e);
            }
            return vo;
        }

        @Override
        protected void onPostExecute(HostVo hostVo) {
            super.onPostExecute(hostVo);
            if (vpn != null) {
                vpn.setClickable(true);
            }
        }
    }

    /** 连接超时看门狗：没在限定时间内连上就重试一次，再失败则报错。 */
    /**
     * 总超时看门狗：不是重试器。
     * <p>
     * 仓库层（{@code CharonVpnSessionRepository}）已经在做指数退避重连，
     * 这里只负责一件事——如果 {@code VPN_CONNECT_TIMEOUT} 内仍未连上，
     * 认为这次尝试彻底失败，主动断开并提示用户，而不是在 UI 层再叠加一套
     * 重试逻辑（两套重试逻辑同时跑会互相打架，且无法互相感知对方的退避节奏）。
     */
    class VpnCheckTask implements Runnable {
        @Override
        public void run() {
            if (repo.current().isConnected() || repo.current().hasError()) {
                return;
            }
            connectingStarted.set(false);
            repo.disconnect();
            ToastUtil.showShort(R.string.error_network_retry);
            imgError();
        }
    }
}
