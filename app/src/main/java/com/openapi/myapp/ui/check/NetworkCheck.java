package com.openapi.myapp.ui.check;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.common.util.GsonUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.bean.vo.IpAdress;
import com.openapi.myapp.bean.vo.MyIpVo;
import com.openapi.myapp.ui.globe.GeoCatalog;
import com.openapi.myapp.data.BaseService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        // 出口 IP 和城市都从我们的服务器拿：App 的请求经过 VPN，服务端看到的就是节点出口。
        MyIpVo myIp = fetchMyIp(context);
        String exitIp = myIp.ip;
        String city = emptyToNull(myIp.city);
        String country = null;
        double[] ll = city == null ? null : GeoCatalog.lookup(city, myIp.province);

        // 服务端没查到城市时，再试一次第三方（可选，失败不影响结果）
        if (ll == null) {
            try {
                IpAdress geo = fetchGeo();
                ll = GeoCatalog.lookup(geo.geoplugin_city, geo.geoplugin_countryName);
                city = city != null ? city : emptyToNull(geo.geoplugin_city);
                country = emptyToNull(geo.geoplugin_countryName);
            } catch (IOException e) {
                LogUtil.w("网络体检：位置查询失败，只显示出口 IP: " + e.getMessage());
            }
        }
        double lat = ll == null ? NO_VALUE : ll[0];
        double lng = ll == null ? NO_VALUE : ll[1];

        String nodeHost = ExitIpStore.getNodeGateway(context);
        if (nodeHost.isEmpty()) {
            nodeHost = null;
        }
        List<String> nodeIps = resolve(nodeHost);

        Result result = new Result(exitIp, city, country, lat, lng, nodeHost, nodeIps);
        if (result.hasLocation) {
            String english = GeoCatalog.displayName(city, country);
            ExitIpStore.saveExit(context, lat, lng, english != null ? english : "Exit");
        }
        return result;
    }

    /** 从服务端取请求方 IP 与城市（经 BaseService，带 App 的鉴权 header）。 */
    @NonNull
    private static MyIpVo fetchMyIp(@NonNull Context context) throws IOException {
        BaseService service = new BaseService();
        service.setup(context);
        try {
            MyIpVo vo = service.getData(Constants.getUrl(Constants.API_MY_IP_URL), MyIpVo.class, "NET_CHECK_IP_TAG");
            if (vo == null || vo.ip == null || vo.ip.isEmpty()) {
                throw new IOException("服务端未返回出口 IP");
            }
            return vo;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("出口 IP 查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询出口 IP 的归属地。先用 geoplugin（http），失败再用 ipapi.co（https）。
     * 两个都失败时，错误信息里带上两边的原因，方便排查。
     */
    @NonNull
    private static IpAdress fetchGeo() throws IOException {
        IOException first;
        try {
            return fetchGeoPlugin();
        } catch (IOException e) {
            first = e;
        }
        try {
            return fetchIpApiCo();
        } catch (IOException e) {
            throw new IOException("geoplugin: " + first.getMessage() + "; ipapi.co: " + e.getMessage(), e);
        }
    }

    @NonNull
    private static IpAdress fetchGeoPlugin() throws IOException {
        String body = get(Constants.IP_GEO_SELF);
        IpAdress geo = GsonUtils.getInstance().fromJson(body, IpAdress.class);
        if (geo == null || geo.geoplugin_request == null) {
            throw new IOException("geoplugin 返回格式不对");
        }
        return geo;
    }

    /** ipapi.co 的字段与 IpAdress 不同，这里手动映射。 */
    @NonNull
    private static IpAdress fetchIpApiCo() throws IOException {
        String body = get("https://ipapi.co/json/");
        JsonObject json;
        try {
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("ipapi.co 返回格式不对");
        }
        if (json.has("error")) {
            throw new IOException("ipapi.co: " + json.get("reason"));
        }
        IpAdress geo = new IpAdress();
        geo.geoplugin_request = str(json, "ip");
        geo.geoplugin_city = str(json, "city");
        geo.geoplugin_region = str(json, "region");
        geo.geoplugin_countryCode = str(json, "country_code");
        geo.geoplugin_countryName = str(json, "country_name");
        geo.geoplugin_latitude = str(json, "latitude");
        geo.geoplugin_longitude = str(json, "longitude");
        return geo;
    }

    @Nullable
    private static String str(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    @NonNull
    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
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
