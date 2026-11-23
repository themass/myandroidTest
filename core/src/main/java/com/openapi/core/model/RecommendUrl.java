package com.openapi.core.model;

/**
 * 频道推荐里的地址拼装。
 *
 * 服务端经常把 {@link RecommendVo#actionUrl}/{@link RecommendVo#img} 写成相对路径
 *（例如 {@code /cat/1023.html}），真正的站点在 {@link RecommendVo#baseurl}。
 * 不拼的话：点击时 {@code Uri.getScheme()} 为 null，会被误报成「版本太低」；
 * 缩略图 Glide 也拿不到可加载的绝对 URL。
 */
public final class RecommendUrl {

    private RecommendUrl() {
    }

    /**
     * @return 可直接交给 Intent / Glide 的地址；{@code path} 为 null 时返回 null；
     *         空字符串原样返回。
     */
    public static String resolve(String baseurl, String path) {
        if (path == null) {
            return null;
        }
        if (path.isEmpty()) {
            return path;
        }
        if (hasScheme(path)) {
            return path;
        }
        if (baseurl == null || baseurl.trim().isEmpty()) {
            return path;
        }
        String base = baseurl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }

    private static boolean hasScheme(String path) {
        int colon = path.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        // http(s)://、sound://、videoList:// …；排除 Windows 盘符式噪音
        for (int i = 0; i < colon; i++) {
            char c = path.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9') && c != '+' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }
}
