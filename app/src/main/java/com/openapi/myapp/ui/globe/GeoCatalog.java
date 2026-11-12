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

    /** 关键字（英文或中文）→ 英文国家名，用于地球标签的 "城市, 国家" 显示。 */
    /**
     * 关键字 → 国家简称。
     * <p>
     * 用简称而不是全称：地球上的标签是"城市, 国家"拼在一颗胶囊里，"U.s.a, United States"
     * 这种会占掉大半个屏幕、还会盖住别的节点。简称统一取 ETIAS/国际通行的短名。
     */
    private static final String[][] COUNTRIES = {
            {"china", "CN"}, {"中国", "CN"},
            // 美国统一显示 USA（不是 US）：节点名里 U.S.A / USA / US- 都是同一个国家，
            // 以前 u.s.a 被当成城市、和国家码拼成 "U.s.a, US"，既长又重复
            {"u.s.a", "USA"}, {"usa", "USA"}, {"u.s.", "USA"}, {"us-", "USA"},
            {"united states", "USA"}, {"美国", "USA"},
            {"japan", "JP"}, {"日本", "JP"},
            {"korea", "KR"}, {"韩国", "KR"},
            {"taiwan", "TW"}, {"台湾", "TW"},
            {"hong kong", "HK"}, {"香港", "HK"},
            {"singapore", "SG"}, {"新加坡", "SG"},
            {"malaysia", "MY"}, {"马来西亚", "MY"},
            {"thailand", "TH"}, {"泰国", "TH"},
            {"indonesia", "ID"}, {"印尼", "ID"},
            {"vietnam", "VN"}, {"越南", "VN"},
            {"india", "IN"}, {"印度", "IN"},
            {"uae", "AE"}, {"迪拜", "AE"},
            {"turkey", "TR"}, {"土耳其", "TR"},
            {"russia", "RU"}, {"俄罗斯", "RU"},
            {"united kingdom", "UK"}, {"uk", "UK"}, {"england", "UK"}, {"英国", "UK"},
            {"france", "FR"}, {"法国", "FR"},
            {"germany", "DE"}, {"德国", "DE"},
            {"italy", "IT"}, {"意大利", "IT"},
            {"canada", "CA"}, {"加拿大", "CA"},
            {"brazil", "BR"}, {"巴西", "BR"},
            {"australia", "AU"}, {"澳大利亚", "AU"},
            {"netherlands", "NL"}, {"holland", "NL"}, {"ned", "NL"}, {"荷兰", "NL"},
            {"poland", "PL"}, {"波兰", "PL"},
            {"philippines", "PH"}, {"菲律宾", "PH"},
            {"moldova", "MD"}, {"摩尔多瓦", "MD"},
            {"bulgaria", "BG"}, {"保加利亚", "BG"},
            {"ukraine", "UA"}, {"乌克兰", "UA"},
            {"finland", "FI"}, {"芬兰", "FI"},
            {"lithuania", "LT"}, {"lietuvos", "LT"}, {"立陶宛", "LT"},
            {"israel", "IL"}, {"lsrael", "IL"}, {"以色列", "IL"},
            {"sweden", "SE"}, {"瑞典", "SE"},
            {"spain", "ES"}, {"西班牙", "ES"},
            {"greece", "GR"}, {"希腊", "GR"},
            {"hungary", "HU"}, {"匈牙利", "HU"},
            {"romania", "RO"}, {"罗马尼亚", "RO"},
            {"belgium", "BE"}, {"比利时", "BE"},
            {"switzerland", "CH"}, {"瑞士", "CH"},
            {"libya", "LY"}, {"利比亚", "LY"},
            {"macau", "MO"}, {"macao", "MO"}, {"澳门", "MO"},
    };

    /**
     * 地球标签用的英文显示名："城市, 国家"；只有国家时返回国家名；都识别不了返回 null。
     * 只用 ASCII 文本，WebView 字体才能正常显示。
     */
    @Nullable
    public static String displayName(@Nullable String... texts) {
        if (texts == null) {
            return null;
        }
        String city = null;
        String country = null;
        for (String text : texts) {
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (country == null) {
                country = countryOf(lower);
            }
            if (city == null) {
                city = cityOf(lower);
            }
        }
        if (city == null) {
            return country;
        }
        if (country == null) {
            return city;
        }
        // 城市名其实就是国家占位（U.S.A / USA / France 这类），只留国家：
        // 以前会拼成 "U.s.a, US"，占掉大半屏幕还盖住旁边的节点
        if (isCountryKey(city.toLowerCase(Locale.ROOT))
                || country.equalsIgnoreCase(city)
                || city.toUpperCase(Locale.ROOT).equals(country)) {
            return country;
        }
        return city + ", " + country;
    }

    @Nullable
    private static String countryOf(String lower) {
        for (String[] row : COUNTRIES) {
            if (lower.contains(row[0])) {
                return row[1];
            }
        }
        return null;
    }

    /** 城市的英文名：坐标相同、关键字为 ASCII 且不是国家名的那一条。 */
    @Nullable
    private static String cityOf(String lower) {
        for (Object[] row : CITIES) {
            String key = (String) row[0];
            if (!lower.contains(key) || isCountryKey(key)) {
                continue;
            }
            for (Object[] other : CITIES) {
                String otherKey = (String) other[0];
                if (otherKey.matches("[a-z .\\-/]+") && !isCountryKey(otherKey)
                        && (Double) other[1] == (Double) row[1] && (Double) other[2] == (Double) row[2]) {
                    return capitalize(otherKey);
                }
            }
        }
        return null;
    }

    private static boolean isCountryKey(String key) {
        for (String[] row : COUNTRIES) {
            if (row[0].equals(key)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 地球上的标签用的英文城市名（WebView 字体不含中文字形，中文会显示成 ???）。
     * 同一坐标在表里若有英文关键字，取它首字母大写的形式；匹配不到返回 null。
     */
    @Nullable
    public static String englishName(@Nullable String... texts) {
        double[] target = lookup(texts);
        if (target == null) {
            return null;
        }
        for (Object[] row : CITIES) {
            String key = (String) row[0];
            boolean ascii = key.matches("[a-z .\\-/]+");
            if (ascii && (Double) row[1] == target[0] && (Double) row[2] == target[1]) {
                return capitalize(key);
            }
        }
        return null;
    }

    private static String capitalize(String key) {
        StringBuilder sb = new StringBuilder();
        for (String word : key.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
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
