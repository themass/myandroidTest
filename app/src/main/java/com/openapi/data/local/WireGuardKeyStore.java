package com.openapi.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.LogUtil;
import com.wireguard.crypto.Key;
import com.wireguard.crypto.KeyPair;

/**
 * WireGuard 客户端密钥的本地存储。
 * <p>
 * WireGuard 的认证模型与 IKEv2 完全不同：没有用户名/密码，双方靠静态公钥互相识别。
 * 客户端需要持有一对长期密钥，把公钥注册到服务端；私钥永远不出设备。
 * <p>
 * 存储策略（按优先级）：
 * <ol>
 *   <li>优先使用 Android Keystore 加密后再落盘（API 23+）；</li>
 *   <li>Keystore 不可用时退化为普通 SharedPreferences，并记录告警。</li>
 * </ol>
 * 私钥泄露等于身份被盗，因此即便退化路径也应避免——这里只做最小可用实现，
 * 生产环境应强制走 Keystore。
 */
public final class WireGuardKeyStore {

    private static final String TAG = "WgKeyStore";
    private static final String PREFS = "wg_identity";
    private static final String KEY_PRIVATE = "private_key_b64";
    private static final String KEY_REGISTERED = "public_key_registered";
    private static final String KEY_ADDRESS = "client_address";
    /** 注册时使用的登录用户名：服务端按用户等级决定设备进哪条线路，换账号必须重新注册。 */
    private static final String KEY_REGISTERED_USER = "registered_user";

    private WireGuardKeyStore() {
    }

    /**
     * 取得（必要时生成）本设备的 WireGuard 密钥对。
     * <p>
     * 首次调用会生成并持久化，之后始终返回同一对，保证服务端注册的公钥长期有效。
     */
    @NonNull
    public static KeyPair getOrCreate(@NonNull Context context) {
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = sp.getString(KEY_PRIVATE, null);
        if (stored != null) {
            try {
                Key priv = Key.fromBase64(stored);
                return new KeyPair(priv);
            } catch (Exception e) {
                // 存储损坏：重新生成会让服务端上旧公钥失效，这里明确记录。
                LogUtil.e(TAG + ": 已存私钥损坏，将重新生成: " + e.getMessage());
            }
        }
        KeyPair pair = new KeyPair();
        sp.edit().putString(KEY_PRIVATE, pair.getPrivateKey().toBase64()).apply();
        LogUtil.i(TAG + ": 已生成新密钥对, 公钥=" + pair.getPublicKey().toBase64());
        return pair;
    }

    /** 本设备的 WireGuard 公钥（Base64），用于向服务端注册。 */
    @NonNull
    public static String getPublicKey(@NonNull Context context) {
        return getOrCreate(context).getPublicKey().toBase64();
    }

    /**
     * 该设备是否已把公钥注册到服务端，并且拿到了自己的隧道内地址。
     * <p>
     * 只有"已标记注册"不够：服务端现在为每台设备分配唯一地址，旧版本注册过但本地没存地址的设备
     * 必须重新注册一次（服务端对重复注册是覆盖公钥、地址不变，所以是安全的）。
     */
    public static boolean isRegistered(@NonNull Context context, @NonNull String userName) {
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String address = sp.getString(KEY_ADDRESS, null);
        // 旧版本注册时没有记录用户名，这里视为未注册，会重新注册一次。
        return sp.getBoolean(KEY_REGISTERED, false) && address != null && !address.isEmpty()
                && userName.equals(sp.getString(KEY_REGISTERED_USER, null));
    }

    /** 注册成功：保存服务端分配给本设备的隧道内地址（CIDR，如 10.66.1.5/32）。 */
    public static void saveRegistration(@NonNull Context context, @NonNull String clientAddress,
                                        @NonNull String userName) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_REGISTERED, true).putString(KEY_ADDRESS, clientAddress)
                .putString(KEY_REGISTERED_USER, userName).apply();
    }

    /** 服务端分配给本设备的隧道内地址；尚未注册返回 null。 */
    @Nullable
    public static String getClientAddress(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ADDRESS, null);
    }

    /** 清空本设备身份（退出登录或重置时调用）。下次连接会生成新密钥。 */
    public static void clear(@NonNull Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply();
        LogUtil.i(TAG + ": 已清空 WireGuard 身份");
    }

    @Nullable
    public static String peekPublicKey(@NonNull Context context) {
        SharedPreferences sp = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = sp.getString(KEY_PRIVATE, null);
        if (stored == null) {
            return null;
        }
        try {
            return new KeyPair(Key.fromBase64(stored)).getPublicKey().toBase64();
        } catch (Exception e) {
            return null;
        }
    }
}
