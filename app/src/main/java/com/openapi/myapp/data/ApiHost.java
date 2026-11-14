package com.openapi.myapp.data;

import com.openapi.common.util.LogUtil;
import com.openapi.common.util.PreferenceUtils;
import com.openapi.myapp.base.MyApplication;

/**
 * API 主机选择与故障切换。
 * <p>
 * 为什么要这一层：{@code Constants.BASE_IP} 是编译进 APK 的字面量，主域名一旦在 DNS 层
 * 被投毒或阻断，所有已安装的客户端会同时失联，而它们连"检查更新"都做不到——只能等用户
 * 自己去商店更新。所以必须在运行时能换一个域名。
 * <p>
 * 两个域名：
 * <ul>
 *   <li>主：{@code api.ok123find.top}</li>
 *   <li>备：{@code api.ok124find.top}</li>
 * </ul>
 * 主域名失败时，{@code OkHttpStack} 会在传输层用备域名把同一个请求重发一次（请求体、方法、
 * 头都不变），成功后把选择落盘，后续请求直接走可用的那个，不必每次都等一次超时。
 * <p>
 * 刻意不再使用服务端下发的 {@code domain.json}：那条链路要依赖一个"已知可用"的域名才能拿到
 * 新域名，主域名挂掉时它本身就是不可达的，属于循环依赖。本地这一对域名在编译期确定，
 * 至少不依赖网络。
 */
public final class ApiHost {

    private static final String TAG = "ApiHost";

    /** 主域名。 */
    public static final String PRIMARY = "api.ok123find.top";
    /** 备域名。需要配好 A 记录与证书后才会真正生效。 */
    public static final String BACKUP = "api.ok124find.top";

    /** 最近一次成功使用的主机名。为空表示还没发生过切换，用 PRIMARY。 */
    private static final String KEY_ACTIVE_HOST = "API_ACTIVE_HOST";

    private static volatile String active = null;

    private ApiHost() {
    }

    /** 当前应使用的主机名。首次调用从磁盘读取，之后走内存。 */
    public static String current() {
        String v = active;
        if (v == null) {
            synchronized (ApiHost.class) {
                v = active;
                if (v == null) {
                    v = read();
                    active = v;
                }
            }
        }
        return v;
    }

    /**
     * 记录"这个主机可用"。
     * <p>
     * 只在确实成功后才调用，避免把偶然一次失败的域名记成可用。
     */
    public static void markWorking(String host) {
        if (host == null || host.equals(active)) {
            return;
        }
        if (!PRIMARY.equals(host) && !BACKUP.equals(host)) {
            return;
        }
        LogUtil.i(TAG + ": 切换 API 主机 " + active + " -> " + host);
        active = host;
        try {
            PreferenceUtils.setPrefString(MyApplication.getInstance(), KEY_ACTIVE_HOST, host);
        } catch (Exception e) {
            // 存不进去只影响下次启动的初始选择，不影响本次运行
            LogUtil.e(TAG + ": 保存主机失败", e);
        }
    }

    /**
     * 给定一个 URL，返回"换另一个域名"的版本；识别不出本服务域名时返回 null。
     * <p>
     * 只在两个已知主机之间切换，绝不碰第三方域名（广告、IP 查询等）。
     * 用{@link #hostOf} 精确比对主机名，而不是 {@code url.contains(...)}：
     * 后者会把 {@code api.ok123find.top.example.com} 这种相似域名当成自家域名。
     */
    public static String failoverUrl(String url) {
        String host = hostOf(url);
        if (host == null) {
            return null;
        }
        // 只替换第一次出现，且必须落在主机名位置上——
        // hostOf 已经保证主机名精确匹配，这里直接替换即可。
        return url.replaceFirst(java.util.regex.Pattern.quote(host),
                PRIMARY.equals(host) ? BACKUP : PRIMARY);
    }

    /**
     * 从 URL 中取出主机名；不是本服务的两个域名之一时返回 null。
     * <p>
     * 不用 {@code android.net.Uri}（单元测试里不可用），也不在 Android 侧依赖它。
     */
    public static String hostOf(String url) {
        String host = rawHost(url);
        if (PRIMARY.equals(host) || BACKUP.equals(host)) {
            return host;
        }
        return null;
    }

    /** 解析出 URL 的主机名（去掉端口），解析不出返回 null。 */
    static String rawHost(String url) {
        if (url == null) {
            return null;
        }
        int start = url.indexOf("://");
        if (start < 0) {
            return null;
        }
        start += 3;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        if (end <= start) {
            return null;
        }
        String authority = url.substring(start, end);
        // 去掉 userinfo@
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        // 去掉端口。IPv6 的 [::1]:443 这里不需要支持——本服务的域名不含冒号。
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            authority = authority.substring(0, colon);
        }
        return authority.isEmpty() ? null : authority;
    }

    private static String read() {
        try {
            String v = PreferenceUtils.getPrefString(
                    MyApplication.getInstance(), KEY_ACTIVE_HOST, PRIMARY);
            if (BACKUP.equals(v) || PRIMARY.equals(v)) {
                return v;
            }
        } catch (Exception e) {
            LogUtil.e(TAG + ": 读取主机失败", e);
        }
        return PRIMARY;
    }
}
