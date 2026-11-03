package com.openapi.data.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.openapi.common.util.DeviceInfoUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.data.local.WireGuardKeyStore;
import com.openapi.myapp.bean.vo.RetInfoVo;
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

    public static void ensureRegistered(@NonNull Context context, @NonNull Callback callback) {
        if (WireGuardKeyStore.isRegistered(context)) {
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
                new CommonResponse.ResponseOkListener<RetInfoVo>() {
                    @Override
                    public void onResponse(RetInfoVo vo) {
                        // 能走到这里说明 errno == 0；RetInfoVo 只是承接 data 字段，
                        // 不承载成功与否的判断（那是 parserData 的职责）。
                        WireGuardKeyStore.markRegistered(context, true);
                        LogUtil.i(TAG + ": 公钥注册成功");
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
                "wg_register", RetInfoVo.class);
    }
}
