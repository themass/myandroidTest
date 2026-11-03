package com.openapi.myapp.ui.user;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.compose.ui.platform.ComposeView;

import com.openapi.common.util.FileSizeUtil;
import com.openapi.common.util.FileUtils;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.common.util.ShareUtil;
import com.openapi.common.util.StringUtils;
import com.openapi.common.util.SystemUtils;
import com.openapi.common.util.ToastUtil;
import com.openapi.ks.free1.R;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.data.local.ProtocolPreferences;
import com.openapi.data.local.SplitTunnelingPreferences;
import com.openapi.data.local.VlessProfileStore;
import com.openapi.data.remote.ShareLinkParser;
import com.openapi.domain.model.SplitTunnelingPolicy;
import com.openapi.domain.model.TunnelProtocol;
import com.openapi.myapp.base.AppContainer;
import com.openapi.myapp.base.MyApplication;
import com.openapi.myapp.bean.vo.InviteVo;
import com.openapi.myapp.bean.vo.StateUseVo;
import com.openapi.myapp.bean.vo.UserInfoVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.myapp.data.UserLoginUtil;
import com.openapi.myapp.data.VersionUpdater;
import com.openapi.myapp.ui.base.app.BaseSingleActivity;
import com.openapi.yewu.net.request.CommonResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 设置页。界面为 Compose（{@link SettingsScreen}），这里只负责：
 * 填充显示状态、处理开关写入偏好、以及各行的业务动作（VPN 设置、VLESS 节点、缓存等）。
 */
public class SettingActivity extends BaseSingleActivity {

    private final SettingsUi ui = new SettingsUi();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_setting);
        setToolbarTitle(R.string.setting, true);
        ComposeView compose = findViewById(R.id.settings_compose);
        SettingsScreenKt.bindSettingsScreen(compose, ui, new SettingsActions() {
            @Override
            public void onToggle(String key, boolean value) {
                saveToggle(key, value);
            }

            @Override
            public void onRow(String key) {
                handleRow(key);
            }
        });
        refreshAll();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll();
    }

    /** 重新读取所有显示值（从对话框或其它页面返回后会变化）。 */
    private void refreshAll() {
        setStateUse();
        setScore();
        setVersion();
        setCache();
        setProtocol();
        setSplitTunnel();
        setKillSwitch();
        setVlessReality();
        ui.setNotify(PreferenceUtils.getPrefBoolean(this, Constants.NOTIFY_SWITCH, true));
        ui.setListCard(PreferenceUtils.getPrefBoolean(this, Constants.LIST_CARD_STYLE, true));
        ui.setPulse(PreferenceUtils.getPrefBoolean(this, Constants.PULSE_STYLE, true));
        ui.setGlobe(PreferenceUtils.getPrefBoolean(this, Constants.GLOBE_VIEW, true));
        ui.setNewHome(PreferenceUtils.getPrefBoolean(this, Constants.NEW_HOME_LAYOUT, true));
        ui.setProtonHome(PreferenceUtils.getPrefBoolean(this, Constants.PROTON_HOME, false));
    }

    private void saveToggle(String key, boolean value) {
        switch (key) {
            case "notify":
                PreferenceUtils.setPrefBoolean(this, Constants.NOTIFY_SWITCH, value);
                break;
            case "list_card":
                PreferenceUtils.setPrefBoolean(this, Constants.LIST_CARD_STYLE, value);
                break;
            case "pulse":
                PreferenceUtils.setPrefBoolean(this, Constants.PULSE_STYLE, value);
                break;
            case "globe":
                PreferenceUtils.setPrefBoolean(this, Constants.GLOBE_VIEW, value);
                break;
            case "new_home":
                PreferenceUtils.setPrefBoolean(this, Constants.NEW_HOME_LAYOUT, value);
                break;
            case "proton_home":
                PreferenceUtils.setPrefBoolean(this, Constants.PROTON_HOME, value);
                break;
            default:
                break;
        }
    }

    private void handleRow(String key) {
        switch (key) {
            case "kill_switch":
                onKillSwitch();
                break;
            case "split_tunnel":
                SplitTunnelingActivity.start(this);
                break;
            case "net_check":
                com.openapi.myapp.ui.check.NetworkCheckActivity.start(this);
                break;
            case "protocol":
                onProtocol();
                break;
            case "vless":
                onVlessReality();
                break;
            case "score":
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.ABOUT)));
                break;
            case "cache":
                onCache();
                break;
            case "version":
                VersionUpdater.checkUpdate(this, true);
                break;
            case "change_password":
                onChangePassword();
                break;
            case "share":
                showShare();
                break;
            case "about":
                com.openapi.myapp.ui.user.SupportActivity.start(this, com.openapi.myapp.ui.user.SupportActivity.PAGE_ABOUT);
                break;
            case "feedback":
                com.openapi.myapp.ui.user.SupportActivity.start(this, com.openapi.myapp.ui.user.SupportActivity.PAGE_FEEDBACK);
                break;
            case "contact":
                com.openapi.myapp.ui.user.SupportActivity.start(this, com.openapi.myapp.ui.user.SupportActivity.PAGE_CONTACT);
                break;
            case "privacy":
                com.openapi.myapp.ui.user.SupportActivity.start(this, com.openapi.myapp.ui.user.SupportActivity.PAGE_PRIVACY);
                break;
            default:
                break;
        }
    }

    private void setStateUse() {
        StateUseVo vo = PreferenceUtils.getPrefObj(this, Constants.USER_STATUS, StateUseVo.class);
        if (vo != null) {
            ui.setTimeUse(vo.timeUse);
            ui.setTraffic(vo.trafficUse);
        } else {
            ui.setTimeUse("0 H");
            ui.setTraffic("0 Gb");
        }
    }

    private void setScore() {
        UserInfoVo vo = UserLoginUtil.getUserCache();
        if (vo != null) {
            ui.setScore(vo.score + "");
        } else {
            ui.setScore(PreferenceUtils.getPrefInt(this, Constants.SCORE_TMP, 0) + "");
        }
    }

    private void setVersion() {
        ui.setVersion(VersionUpdater.getVersion());
    }

    private void setCache() {
        ui.setCache(FileSizeUtil.getAutoFileOrFilesSize(FileUtils.getWriteFilePath(this)));
    }

    private void onCache() {
        ToastUtil.showShort(R.string.menu_btn_cache_context);
        FileUtils.deleteFile(new File(FileUtils.getWriteFilePath(this)));
        MyApplication.getInstance().initFilePath();
        setCache();
    }

    /** 显示当前协议偏好：自动或某个具体协议名。 */
    private void setProtocol() {
        TunnelProtocol preferred = ProtocolPreferences.getPreferred(this);
        ui.setProtocol(preferred == null ? getString(R.string.protocol_auto) : preferred.getDisplayName());
    }

    /** 弹出协议选择：自动 + 当前设备真正可用的协议。 */
    private void onProtocol() {
        final List<TunnelProtocol> available = AppContainer.vpnSession().availableProtocols();
        final List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.protocol_auto));
        for (TunnelProtocol p : available) {
            labels.add(p.getDisplayName());
        }
        TunnelProtocol preferred = ProtocolPreferences.getPreferred(this);
        int checkedIndex = 0;
        if (preferred != null) {
            int idx = available.indexOf(preferred);
            if (idx >= 0) {
                checkedIndex = idx + 1;
            }
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.protocol_dialog_title)
                .setSingleChoiceItems(labels.toArray(new String[0]), checkedIndex, (dialog, which) -> {
                    if (which == 0) {
                        ProtocolPreferences.setPreferred(this, null);
                    } else {
                        ProtocolPreferences.setPreferred(this, available.get(which - 1));
                    }
                    setProtocol();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setSplitTunnel() {
        SplitTunnelingPolicy policy = SplitTunnelingPreferences.getPolicy(this);
        ui.setSplitTunnel(policy.isAllThrough()
                ? getString(R.string.split_tunnel_off)
                : getString(R.string.split_tunnel_apps, policy.getPackages().size()));
    }

    private void setKillSwitch() {
        boolean enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                && isAlwaysOnVpnLockedDown();
        ui.setKillSwitch(getString(enabled ? R.string.kill_switch_status_on : R.string.kill_switch_status_off));
    }

    private boolean isAlwaysOnVpnLockedDown() {
        // 只能确认本应用是否被设为 always-on；lockdown 状态应用层读取不到，只能引导用户去确认。
        String alwaysOnApp = android.provider.Settings.Secure.getString(getContentResolver(), "always_on_vpn_app");
        return getPackageName().equals(alwaysOnApp);
    }

    private void onKillSwitch() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.setting_kill_switch)
                .setMessage(R.string.kill_switch_explain)
                .setPositiveButton(android.R.string.ok, (d, w) -> openSystemVpnSettings())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openSystemVpnSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_VPN_SETTINGS));
        } catch (Exception e) {
            ToastUtil.showShort(R.string.vpn_not_supported);
        }
    }

    /** 显示当前已保存的 VLESS+Reality 节点备注名；未配置时提示去添加。 */
    private void setVlessReality() {
        if (!VlessProfileStore.hasProfile(this)) {
            ui.setVless(getString(R.string.vless_reality_not_set));
            return;
        }
        String remark = VlessProfileStore.getRemark(this);
        ui.setVless(getString(R.string.vless_reality_saved, remark != null ? remark : ""));
    }

    /** 粘贴分享链接（vless / vmess / ss / trojan / hy2）。 */
    private void onVlessReality() {
        boolean has = VlessProfileStore.hasProfile(this);
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.vless_reality_dialog_hint);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.vless_reality_dialog_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String link = input.getText().toString().trim();
                    if (link.isEmpty()) {
                        return;
                    }
                    try {
                        String remark = VlessProfileStore.save(this, link);
                        ToastUtil.showShort(getString(R.string.vless_reality_saved, remark != null ? remark : link));
                        setVlessReality();
                    } catch (ShareLinkParser.ParseException e) {
                        ToastUtil.showShort(getString(R.string.vless_reality_parse_failed, e.getMessage()));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (has) {
            builder.setNeutralButton(R.string.vless_reality_clear, (d, w) -> {
                VlessProfileStore.clear(this);
                setVlessReality();
            });
        }
        builder.show();
    }

    private void onContract() {
        SystemUtils.copy(SettingActivity.this, Constants.SUPPORT_EMAIL);
        ToastUtil.showShort(R.string.menu_copy_emai);
    }

    /** 修改密码：需要已登录；未登录时提示。 */
    private void onChangePassword() {
        if (UserLoginUtil.getUserCache() == null) {
            ToastUtil.showShort(R.string.password_need_login);
            return;
        }
        startActivity(new Intent(this, com.openapi.myapp.ui.account.PasswordActivity.class)
                .putExtra(com.openapi.myapp.ui.account.PasswordActivity.EXTRA_MODE,
                        com.openapi.myapp.ui.account.PasswordActivity.MODE_CHANGE));
    }

    /** 分享：已登录先取邀请码印在图上，取不到或未登录则只分享链接。 */
    private void showShare() {
        if (UserLoginUtil.getUserCache() == null) {
            com.openapi.myapp.ui.share.ShareCard.INSTANCE.share(this, null);
            return;
        }
        BaseService service = new BaseService();
        service.setup(this);
        service.getData(Constants.getUrl(Constants.API_INVITE_URL),
                new CommonResponse.ResponseOkListener<InviteVo>() {
                    @Override
                    public void onResponse(InviteVo vo) {
                        super.onResponse(vo);
                        com.openapi.myapp.ui.share.ShareCard.INSTANCE.share(SettingActivity.this,
                                vo == null ? null : vo.code);
                    }
                },
                new CommonResponse.ResponseErrorListener() {
                    @Override
                    protected void onError() {
                        super.onError();
                        com.openapi.myapp.ui.share.ShareCard.INSTANCE.share(SettingActivity.this, null);
                    }
                }, "SHARE_INVITE_TAG", InviteVo.class);
    }

    @Override
    public boolean needShow() {
        return false;
    }

    @Override
    protected boolean enableSliding() {
        return true;
    }

    @Override
    protected AdsContext.Categrey getBannerCategrey() {
        return AdsContext.Categrey.CATEGREY_VPN2;
    }
}
