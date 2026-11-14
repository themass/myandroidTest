package com.openapi.myapp.data;

import com.openapi.common.util.LogUtil;

/**
 * 插屏广告的时机闸门。
 * <p>
 * 为什么需要：AdMob 的插屏政策要求插屏不能打断用户正在进行的核心操作，且两次插屏之间要有间隔。
 * 本应用的核心操作是"连接 / 断开 VPN" —— 用户点连接后马上弹一个全屏广告，既打断操作，
 * 也容易被审核判定为"广告干扰核心功能"。另外各页面各自调用 {@code AdsContext.showRand()}，
 * 分散在 5 处，靠逐一修改容易漏。
 * <p>
 * 所以把判断集中到这里：任何一次 VPN 状态切换都会记一个时间戳，闸门在之后的一段时间内
 * 一律拒绝插屏。调用方不需要知道这个规则，只要照常调 {@code showRand()}。
 */
public final class AdGate {

    private static final String TAG = "AdGate";

    /**
     * VPN 操作后的静默期。这个窗口内不弹任何插屏：
     * 用户刚点完连接/断开，视线在连接按钮和状态文本上，此时弹全屏广告是典型的打断。
     */
    private static final long VPN_QUIET_MS = 20_000L;

    /** 两次插屏之间的最小间隔，避免连续切页时连弹。 */
    private static final long MIN_GAP_MS = 45_000L;

    /** 最近一次 VPN 操作（连接、断开、切换节点）的时间。 */
    private static volatile long lastVpnActionAt = 0L;

    /** 最近一次真正展示了插屏的时间。 */
    private static volatile long lastShownAt = 0L;

    private AdGate() {
    }

    /**
     * 记录一次 VPN 操作。在用户点连接/断开的入口调用一次即可。
     * 通知栏磁贴、悬浮窗、通知按钮等所有入口都应经过同一个方法，否则闸门会失效。
     */
    public static void noteVpnAction() {
        lastVpnActionAt = System.currentTimeMillis();
    }

    /**
     * 当前是否允许展示插屏。
     * <p>
     * 这里只判断时机，不判断"该不该有广告"（那是 {@code AdsContext.rateShow()} 的概率控制）。
     * 两者是"与"的关系：先过时机闸门，再过概率。
     */
    public static boolean allowInterstitial() {
        long now = System.currentTimeMillis();

        long sinceVpn = now - lastVpnActionAt;
        if (lastVpnActionAt > 0 && sinceVpn < VPN_QUIET_MS) {
            LogUtil.i(TAG + ": 距上次 VPN 操作 " + sinceVpn + "ms，跳过插屏");
            return false;
        }

        long sinceShown = now - lastShownAt;
        if (lastShownAt > 0 && sinceShown < MIN_GAP_MS) {
            LogUtil.i(TAG + ": 距上次插屏 " + sinceShown + "ms，跳过插屏");
            return false;
        }
        return true;
    }

    /** 插屏确实展示后调用，用于计算最小间隔。 */
    public static void noteInterstitialShown() {
        lastShownAt = System.currentTimeMillis();
    }
}
