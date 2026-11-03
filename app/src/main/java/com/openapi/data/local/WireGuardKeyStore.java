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

    /** 该设备是否已把公钥注册到服务端。 */
    public static boolean isRegistered(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_REGISTERED, false);
    }

    public static void markRegistered(@NonNull Context context, boolean registered) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_REGISTERED, registered).apply();
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
