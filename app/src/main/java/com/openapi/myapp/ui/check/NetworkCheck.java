package com.openapi.myapp.ui.check;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.GsonUtils;
import com.openapi.myapp.bean.vo.IpAdress;
import com.openapi.myapp.constant.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 网络体检：查询"当前出口 IP"的归属地，并和最近连接的节点地址对比。
 * <p>
 * 出口 IP 的判断方式：geoplugin 不带参数时返回的是发起请求那一端的 IP。
 * 在 VPN 连接中发起，这个 IP 就是节点的出口；未连接或本应用被分应用代理排除时，
 * 它是本机真实出口，和节点 IP 不一致。
 * <p>
 * 必须在后台线程调用。
 */
public final class NetworkCheck {

    private static final int TIMEOUT_MS = 8000;
    private static final double NO_VALUE = Double.NaN;

    private NetworkCheck() {
    }

    /** 一次检测的结果。字段都可能为空，UI 需要处理。 */
    public static final class Result {
        @Nullable
        public final String exitIp;
        @Nullable
        public final String city;
        @Nullable
        public final String country;
        public final double lat;
        public final double lng;
        public final boolean hasLocation;
        @Nullable
        public final String nodeHost;
        @NonNull
        public final List<String> nodeIps;
        public final boolean matched;

        Result(@Nullable String exitIp, @Nullable String city, @Nullable String country,
               double lat, double lng, @Nullable String nodeHost, @NonNull List<String> nodeIps) {
            this.exitIp = exitIp;
            this.city = city;
            this.country = country;
            this.lat = lat;
            this.lng = lng;
            this.hasLocation = !Double.isNaN(lat) && !Double.isNaN(lng);
            this.nodeHost = nodeHost;
            this.nodeIps = nodeIps;
            this.matched = exitIp != null && nodeIps.contains(exitIp);
        }
    }

    /**
     * 执行一次检测。成功时把出口位置写入 {@link ExitIpStore}，首页地球会用它标记。
     *
     * @throws IOException 网络请求失败（超时、无网络等）
     */
    @NonNull
    public static Result run(@NonNull Context context) throws IOException {
        IpAdress geo = fetchGeo();
        double lat = parseCoord(geo.geoplugin_latitude);
        double lng = parseCoord(geo.geoplugin_longitude);
        String city = emptyToNull(geo.geoplugin_city);
        String country = emptyToNull(geo.geoplugin_countryName);

        String nodeHost = ExitIpStore.getNodeGateway(context);
        if (nodeHost.isEmpty()) {
            nodeHost = null;
        }
        List<String> nodeIps = resolve(nodeHost);

        Result result = new Result(emptyToNull(geo.geoplugin_request), city, country, lat, lng, nodeHost, nodeIps);
        if (result.hasLocation) {
            String label = city != null ? city : (country != null ? country : "");
            ExitIpStore.saveExit(context, lat, lng, label);
        }
        return result;
    }

    @NonNull
    private static IpAdress fetchGeo() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(Constants.IP_GEO_SELF).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            IpAdress geo = GsonUtils.getInstance().fromJson(sb.toString(), IpAdress.class);
            if (geo == null) {
                throw new IOException("geoplugin 返回为空");
            }
            return geo;
        } finally {
            conn.disconnect();
        }
    }

    /** 把节点地址解析成 IP 列表；域名可能对应多个 IP，地址本身就是 IP 时直接返回。 */
    @NonNull
    private static List<String> resolve(@Nullable String host) {
        List<String> ips = new ArrayList<>();
        if (host == null) {
            return ips;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                ips.add(address.getHostAddress());
            }
        } catch (UnknownHostException e) {
            // 解析失败就返回空列表，UI 会提示"无法解析节点地址"，不抛异常打断检测。
        }
        return ips;
    }

    private static double parseCoord(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return NO_VALUE;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return NO_VALUE;
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
