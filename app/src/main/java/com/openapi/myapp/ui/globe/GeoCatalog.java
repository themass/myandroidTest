package com.openapi.myapp.ui.globe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.TimeZone;

/**
 * 节点城市名 → 经纬度。
 * <p>
 * 服务端的节点数据目前没有经纬度字段，只有城市/地区名，所以在客户端维护一张常用城市表。
 * 匹配是子串、不区分大小写：节点名或英文名里包含关键字即命中。表外的节点不会显示在地球上。
 * 后续若服务端下发经纬度，直接用服务端值，这张表只作为兜底。
 */
public final class GeoCatalog {

    /** 关键字（小写英文或中文）→ {lat, lng}。 */
    private static final Object[][] CITIES = {
            {"hong kong", 22.32, 114.17}, {"香港", 22.32, 114.17},
            {"tokyo", 35.68, 139.69}, {"japan", 35.68, 139.69}, {"日本", 35.68, 139.69},
            {"osaka", 34.69, 135.50},
            {"seoul", 37.57, 126.98}, {"korea", 37.57, 126.98}, {"韩国", 37.57, 126.98},
            {"taipei", 25.03, 121.57}, {"taiwan", 25.03, 121.57}, {"台湾", 25.03, 121.57},
            {"singapore", 1.35, 103.82}, {"新加坡", 1.35, 103.82},
            {"kuala lumpur", 3.14, 101.69}, {"malaysia", 3.14, 101.69}, {"马来西亚", 3.14, 101.69},
            {"bangkok", 13.76, 100.50}, {"thailand", 13.76, 100.50}, {"泰国", 13.76, 100.50},
            {"jakarta", -6.21, 106.85}, {"indonesia", -6.21, 106.85}, {"印尼", -6.21, 106.85},
            {"ho chi minh", 10.82, 106.63}, {"vietnam", 21.03, 105.85}, {"越南", 21.03, 105.85},
            {"mumbai", 19.08, 72.88}, {"india", 19.08, 72.88}, {"印度", 19.08, 72.88},
            {"dubai", 25.20, 55.27}, {"uae", 25.20, 55.27}, {"迪拜", 25.20, 55.27},
            {"istanbul", 41.01, 28.98}, {"turkey", 41.01, 28.98}, {"土耳其", 41.01, 28.98},
            {"moscow", 55.76, 37.62}, {"russia", 55.76, 37.62}, {"俄罗斯", 55.76, 37.62},
            {"london", 51.51, -0.13}, {"uk", 51.51, -0.13}, {"united kingdom", 51.51, -0.13}, {"英国", 51.51, -0.13},
            {"paris", 48.86, 2.35}, {"france", 48.86, 2.35}, {"法国", 48.86, 2.35},
            {"frankfurt", 50.11, 8.68}, {"berlin", 52.52, 13.41}, {"germany", 50.11, 8.68}, {"德国", 50.11, 8.68},
            {"amsterdam", 52.37, 4.90}, {"netherlands", 52.37, 4.90}, {"荷兰", 52.37, 4.90},
            {"stockholm", 59.33, 18.07}, {"sweden", 59.33, 18.07},
            {"madrid", 40.42, -3.70}, {"spain", 40.42, -3.70},
            {"rome", 41.90, 12.50}, {"italy", 41.90, 12.50},
            {"sydney", -33.87, 151.21}, {"australia", -33.87, 151.21}, {"澳大利亚", -33.87, 151.21},
            {"toronto", 43.65, -79.38}, {"canada", 43.65, -79.38}, {"加拿大", 43.65, -79.38},
            {"los angeles", 34.05, -118.24}, {"san jose", 37.34, -121.89}, {"silicon valley", 37.34, -121.89},
            {"seattle", 47.61, -122.33}, {"chicago", 41.88, -87.63},
            {"new york", 40.71, -74.01}, {"america/new_york", 40.71, -74.01}, {"美国", 37.09, -95.71},
            {"u.s.a", 37.09, -95.71}, {"u.s.", 37.09, -95.71}, {"us-", 37.09, -95.71},
            {"united states", 37.09, -95.71}, {"usa", 37.09, -95.71},
            {"sao paulo", -23.55, -46.63}, {"brazil", -23.55, -46.63}, {"巴西", -23.55, -46.63},
            {"shanghai", 31.23, 121.47}, {"上海", 31.23, 121.47},
            {"beijing", 39.90, 116.41}, {"北京", 39.90, 116.41},
            {"guangzhou", 23.13, 113.26}, {"shenzhen", 22.54, 114.06}, {"深圳", 22.54, 114.06},
            {"hangzhou", 30.27, 120.15}, {"chengdu", 30.57, 104.07}, {"成都", 30.57, 104.07},
            {"中国", 35.86, 104.19}, {"china", 35.86, 104.19},
    };

    private GeoCatalog() {
    }

    /**
     * 在若干文本里找第一个命中的城市。按传入顺序匹配，同一文本内取表里先出现的关键字。
     *
     * @return {lat, lng}；全部未命中返回 null
     */
    @Nullable
    public static double[] lookup(@Nullable String... texts) {
        if (texts == null) {
            return null;
        }
        for (String text : texts) {
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (Object[] row : CITIES) {
                if (lower.contains((String) row[0])) {
                    return new double[]{(Double) row[1], (Double) row[2]};
                }
            }
        }
        return null;
    }

    /**
     * 用户位置的近似值：取系统时区里的城市名（如 Asia/Shanghai → shanghai）。
     * 不申请定位权限，精度只到城市级；时区无法识别时返回 null。
     */
    @Nullable
    public static double[] approximateUser() {
        String id = TimeZone.getDefault().getID();
        String city = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
        return lookup(city.replace('_', ' '), id);
    }
}
