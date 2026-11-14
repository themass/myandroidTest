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
    /**
     * 备域名。它是新注册的，暂时只有 HTTP，还没有证书。
     * <p>
     * 用 HTTP 是有代价的：登录口令、token 在这条路上是明文。所以只在主域名连不上时
     * 才降级过来，主域名恢复后会自动切回去；等备域名配好证书，把 SCHEME_BACKUP 改成
     * "https" 即可，不需要改客户端逻辑。
     */
    public static final String BACKUP = "api.ok124find.top";

    /** 主域名走 HTTPS（证书含 api.ok123find.top）。 */
    public static final String SCHEME_PRIMARY = "https";
    /** 备域名暂时只支持 HTTP。 */
    public static final String SCHEME_BACKUP = "http";

    /** 最近一次成功使用的主机名。为空表示还没发生过切换，用 PRIMARY。 */
    private static final String KEY_ACTIVE_HOST = "API_ACTIVE_HOST";

    /**
     * 停在备域名时，隔多久用一次真实请求去探主域名是否恢复。
     * <p>
     * 这个探测不是额外开销：探的那一次请求本身就是用户要发的请求，主域名通了就直接用它
     * 的响应。取 5 分钟是为了让"主域名短暂抖动"不会立刻把用户拉回明文通道，
     * 同时故障结束后又能较快回到 HTTPS。
     */
    private static final long PRIMARY_RETRY_INTERVAL_MS = 5 * 60 * 1000L;

    private static volatile String active = null;
    private static volatile long lastPrimaryProbeAt = 0L;

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
     * 是否该拿这次请求去试一下主域名。
     * <p>
     * 只有在"当前停在备域名、且距上次探测超过间隔"时为真。返回 true 的同时会把时间戳
     * 前推，避免一次失败后每个请求都去撞主域名。
     */
    public static boolean dueForPrimaryProbe() {
        if (!BACKUP.equals(current())) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastPrimaryProbeAt < PRIMARY_RETRY_INTERVAL_MS) {
            return false;
        }
        lastPrimaryProbeAt = now;
        return true;
    }

    /** 给定主机名对应的 scheme。未知主机返回 https（保守，不主动降级）。 */
    public static String schemeFor(String host) {
        return BACKUP.equals(host) ? SCHEME_BACKUP : SCHEME_PRIMARY;
    }

    /** 当前主机的 scheme。 */
    public static String currentScheme() {
        return schemeFor(current());
    }

    /** 当前主机上 API 的端口：http 是 80，https 是 443。 */
    public static int currentPort() {
        return "http".equals(currentScheme()) ? 80 : 443;
    }

    /**
     * 记录"这个主机可用"，并在条件满足时切回主域名。
     * <p>
     * 只在确实成功后才调用，避免把偶然一次失败的域名记成可用。
     * <p>
     * 重点：<b>停在备域名不是终态。</b>备域名走的是明文 HTTP，登录口令和 token
     * 在这条路上是明文的。如果主域名只挂了几十秒、之后即使恢复我们也继续用它，
     * 那等于一次短暂故障把所有用户永久降级到明文传输。所以当承载当前成功的请求
     * 已经是主域名时（说明它恢复了），立即切回。
     */
    public static void markWorking(String host) {
        if (host == null) {
            return;
        }
        if (!PRIMARY.equals(host) && !BACKUP.equals(host)) {
            return;
        }
        // 主域名可用 → 无论当前是什么，都回到主域名（含"本来就在主域名"的情况）
        if (PRIMARY.equals(host)) {
            if (!PRIMARY.equals(active)) {
                LogUtil.i(TAG + ": 主域名恢复，切回 " + active + " -> " + PRIMARY);
                persist(PRIMARY);
            }
            return;
        }
        // 备域名可用，且当前还不在备域名 → 降级过去
        if (host.equals(active)) {
            return;
        }
        LogUtil.i(TAG + ": 切换 API 主机 " + active + " -> " + host);
        persist(host);
    }

    private static void persist(String host) {
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
     * <p>
     * <b>协议也要一起换。</b>两个域名的能力不一样：主域名有证书走 https，备域名暂时
     * 只有 http。如果只替换主机名而沿用原 scheme，从主切到备会得到一个
     * {@code https://api.ok124find.top} —— 那是自找的 TLS 握手失败，切换永远不会成功。
     */
    public static String failoverUrl(String url) {
        String host = hostOf(url);
        if (host == null) {
            return null;
        }
        String target = PRIMARY.equals(host) ? BACKUP : PRIMARY;
        String scheme = schemeFor(target);
        // 替换主机名后，再把 scheme 换掉。hostOf 保证 host 是精确匹配，
        // 所以第一次出现就是主机名位置，不会误伤路径里的同名字符串。
        String swapped = url.replaceFirst(java.util.regex.Pattern.quote(host),
                java.util.regex.Matcher.quoteReplacement(target));
        int idx = swapped.indexOf("://");
        if (idx > 0) {
            swapped = scheme + swapped.substring(idx);
        }
        return swapped;
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
