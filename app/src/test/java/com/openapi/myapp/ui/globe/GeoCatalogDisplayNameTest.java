package com.openapi.myapp.ui.globe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 地球标签的显示名。
 * <p>
 * 这条路径会直接影响可读性：标签是"城市, 国家"拼在一颗胶囊里，一旦把国家占位符
 * 当成城市，就会出现 "U.s.a, United States" 这种占掉半屏还盖住旁边节点的文字。
 */
public class GeoCatalogDisplayNameTest {

    @Test
    public void usaShowsAsSingleShortCode() {
        // 节点 ename/name 里常见 U.S.A、USA、US- 三种写法，都应收敛成 USA
        assertEquals("USA", GeoCatalog.displayName(null, "U.S.A", null));
        assertEquals("USA", GeoCatalog.displayName("USA"));
        assertEquals("USA", GeoCatalog.displayName("美国"));
    }

    @Test
    public void countryPlaceholderIsNotRepeatedAsCity() {
        // France 既是国家名也在城市表里（巴黎坐标），不能拼成 "France, FR"
        assertEquals("FR", GeoCatalog.displayName("France"));
        // 美国统一是 USA，不再是 US —— 与节点名里的 U.S.A / USA 保持一致
        assertEquals("USA", GeoCatalog.displayName("United States"));
    }

    @Test
    public void realCityKeepsCountrySuffix() {
        assertEquals("Tokyo, JP", GeoCatalog.displayName("tokyo", "Japan"));
        assertEquals("Frankfurt, DE", GeoCatalog.displayName("frankfurt", "Germany"));
    }

    @Test
    public void cityAndCountrySamePlaceShowsCityOnce() {
        // 香港、新加坡这类城邦：城市名与国家码相同，不能显示 "HK, HK"
        assertEquals("HK", GeoCatalog.displayName("hong kong"));
        assertEquals("SG", GeoCatalog.displayName("singapore"));
    }

    @Test
    public void unknownTextReturnsNull() {
        assertEquals(null, GeoCatalog.displayName("Atlantis"));
    }
}
