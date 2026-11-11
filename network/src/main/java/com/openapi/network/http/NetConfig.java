package com.openapi.network.http;

/**
 * 网络层的业务配置。由应用在启动时设置，commonutil 本身不带任何接口域名、
 * 请求头名或服务端返回码的业务取值。
 *
 * 默认值只是占位（空字符串 / 不回调），应用必须在使用网络前调用 {@link #init}。
 */
public final class NetConfig {

    /** 服务端返回码：成功。 */
    public static final int CODE_SUCCESS = 0;
    /** 服务端返回码：成功，但需要清理登录状态。 */
    public static final int CODE_SUCCESS_CLEAR_LOGIN = 1;
    /** 服务端返回码：需要登录。 */
    public static final int CODE_LOGIN_REQUIRED = 2;

    /** 登录失效回调：收到需要登录的返回码时调用，参数是原始返回码。 */
    public interface AuthListener {
        void onAuthRequired(int code);
    }

    private static String defaultReferer = "";
    private static String tokenHeader = "";
    private static String deviceHeader = "";
    private static AuthListener authListener;

    private NetConfig() {
    }

    /** 启动时调用一次。 */
    public static void init(String defaultReferer, String tokenHeader, String deviceHeader, AuthListener authListener) {
        NetConfig.defaultReferer = defaultReferer == null ? "" : defaultReferer;
        NetConfig.tokenHeader = tokenHeader == null ? "" : tokenHeader;
        NetConfig.deviceHeader = deviceHeader == null ? "" : deviceHeader;
        NetConfig.authListener = authListener;
    }

    public static String defaultReferer() {
        return defaultReferer;
    }

    /** 请求头里放 token 的名字；同时也是偏好设置里保存 token 的 key。 */
    public static String tokenHeader() {
        return tokenHeader;
    }

    /** 请求头里放设备 ID 的名字。 */
    public static String deviceHeader() {
        return deviceHeader;
    }

    public static void notifyAuthRequired(int code) {
        AuthListener l = authListener;
        if (l != null) {
            l.onAuthRequired(code);
        }
    }
}
