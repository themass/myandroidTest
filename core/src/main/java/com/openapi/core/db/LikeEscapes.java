package com.openapi.core.db;

/**
 * LIKE 查询串转义：用户输入里的 {@code %}、{@code _}、{@code \} 是按通配符语法解释的，
 * 搜「100%」却把「100x」也捞出来就是没转义（ChatSearchDaoTest 钉住这一点）。
 * 配合 SQL 里的 {@code ESCAPE '\'} 使用；SQLite 的字符串字面量不转义反斜杠，SQL 写 {@code ESCAPE '\'} 即可。
 */
public final class LikeEscapes {

    private LikeEscapes() {
    }

    /** 原样调用：null 归一为空串；空白串由 DAO 的默认方法直接返回空结果，不会走到这里。 */
    public static String escape(String query) {
        StringBuilder out = new StringBuilder(query.length() + 8);
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '%' || c == '_' || c == '\\') out.append('\\');
            out.append(c);
        }
        return out.toString();
    }
}
