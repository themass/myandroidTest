package com.openapi.data.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.openapi.common.util.DeviceInfoUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.data.local.WireGuardKeyStore;
import com.openapi.myapp.bean.vo.WgRegisterVo;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.data.BaseService;
import com.openapi.yewu.net.request.CommonResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * 把本机 WireGuard 公钥注册到服务端。
 * <p>
 * 后端接口 {@code POST /api/host/wg/register.json} 已实现，见后端仓库
 * {@code HostController#wgRegister}。契约细节见客户端仓库
 * {@code docs/WIREGUARD-BACKEND.md}。
 * <p>
 * 响应格式沿用本项目统一的 {@code {errno, data, error}} 包装：{@code errno == 0}
 * 即成功。这个判断由 {@code BaseRequest.parserData()} 统一完成——失败时请求会走
 * {@code onErrorResponse} 分支，因此这里的成功回调只会在真正成功时触发，
 * 不需要再自己解析错误码。
 * <p>
 * 幂等策略：同一设备只在"从未注册成功"时发起网络请求。服务端对重复注册是
 * upsert（覆盖旧公钥），所以即便本地标记丢失重复提交也是安全的。
 */
public final class WireGuardRegistrationUtil {

    private static final String TAG = "WgRegistration";

    private WireGuardRegistrationUtil() {
    }

    public interface Callback {
        void onReady();

        void onFailed(@NonNull String reason);
    }

    /** 形如 10.66.1.5/32 的 IPv4 CIDR。 */
    static boolean isValidAddress(String address) {
        if (address == null) {
            return false;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})/(\\d{1,2})$")
                .matcher(address.trim());
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            if (Integer.parseInt(m.group(i)) > 255) {
                return false;
            }
        }
        return Integer.parseInt(m.group(5)) <= 32;
    }

    public static void ensureRegistered(@NonNull Context context, @NonNull Callback callback) {
        com.openapi.myapp.bean.vo.UserInfoVo user = com.openapi.myapp.data.UserLoginUtil.getUserCache();
        if (user == null || user.name == null || user.name.isEmpty()) {
            // 注册需要登录；未登录的设备没有所属用户，服务端无法决定它能进哪条线路。
            callback.onFailed("not logged in");
            return;
        }
        if (WireGuardKeyStore.isRegistered(context, user.name)) {
            callback.onReady();
            return;
        }
        String publicKey = WireGuardKeyStore.getPublicKey(context);
        String deviceId = DeviceInfoUtils.getDeviceId(context);

        Map<String, String> body = new HashMap<>();
        body.put("deviceId", deviceId);
        body.put("publicKey", publicKey);

        BaseService service = new BaseService();
        service.setup(context);
        service.postData(Constants.getUrl(Constants.API_WG_REGISTER_URL), body,
                new CommonResponse.ResponseOkListener<WgRegisterVo>() {
                    @Override
                    public void onResponse(WgRegisterVo vo) {
                        // 能走到这里说明 errno == 0。服务端返回本设备唯一的隧道内地址，必须是合法的 IPv4 CIDR：
                        // 老版本服务端只返回 {"ret":true}，没有地址，这里按失败处理，让上层回退到 IKEv2，
                        // 而不是带着空地址去建隧道。
                        String address = vo == null ? null : vo.clientAddress;
                        if (!isValidAddress(address)) {
                            LogUtil.e(TAG + ": 服务端没有返回合法的隧道地址: " + address);
                            callback.onFailed("no client address");
                            return;
                        }
                        WireGuardKeyStore.saveRegistration(context, address, user.name);
                        LogUtil.i(TAG + ": 公钥注册成功，隧道地址 " + address);
                        callback.onReady();
                    }
                },
                new CommonResponse.ResponseErrorListener() {
                    @Override
                    protected void onError() {
                        super.onError();
                        LogUtil.e(TAG + ": 公钥注册失败，本次 WireGuard 连接放弃");
                        callback.onFailed("register failed");
                    }
                },
                "wg_register", WgRegisterVo.class);
    }
}
