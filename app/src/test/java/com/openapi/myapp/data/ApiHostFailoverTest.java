package com.openapi.myapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * {@link ApiHost} 的纯逻辑部分。
 * <p>
 * 只测域名识别的两个纯函数——它们的错误后果最直接：{@code failoverUrl} 一旦误判，
 * 可能把第三方域名（广告、IP 查询）也改写掉，导致请求发到完全无关的主机。
 * 涉及 SharedPreferences 的部分需要 Android 运行环境，不在这里覆盖。
 */
public class ApiHostFailoverTest {

    private static final String PRIMARY = ApiHost.PRIMARY;
    private static final String BACKUP = ApiHost.BACKUP;

    @Test
    public void primarySwitchesToBackupAndDropsToHttp() {
        // 备域名暂时没有证书，切换时必须把 scheme 一起换成 http，
        // 否则会得到一个 https://api.ok124find.top —— 自找 TLS 握手失败。
        String url = "https://" + PRIMARY + "/vvv/api/user/info.json";
        assertEquals("http://" + BACKUP + "/vvv/api/user/info.json", ApiHost.failoverUrl(url));
    }

    @Test
    public void backupSwitchesBackToPrimaryAndRestoresHttps() {
        String url = "http://" + BACKUP + "/vvv/api/user/info.json";
        assertEquals("https://" + PRIMARY + "/vvv/api/user/info.json", ApiHost.failoverUrl(url));
    }

    /** scheme 与主机必须成对：主 https、备 http。 */
    @Test
    public void schemeFollowsHost() {
        assertEquals("https", ApiHost.schemeFor(PRIMARY));
        assertEquals("http", ApiHost.schemeFor(BACKUP));
        // 未知主机不主动降级
        assertEquals("https", ApiHost.schemeFor("api.example.com"));
    }

    /** 第三方域名必须原样返回 null，不能被当成本服务的域名改写。 */
    @Test
    public void thirdPartyDomainsAreIgnored() {
        assertNull(ApiHost.failoverUrl("https://www.geoplugin.net/json.gp"));
        assertNull(ApiHost.failoverUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"));
        assertNull(ApiHost.failoverUrl("http://ip.chinaz.com/getip.aspx"));
    }

    @Test
    public void nullAndEmptyUrlsAreSafe() {
        assertNull(ApiHost.failoverUrl(null));
        assertNull(ApiHost.failoverUrl(""));
        assertNull(ApiHost.hostOf(null));
    }

    @Test
    public void hostOfIdentifiesBothHosts() {
        assertEquals(PRIMARY, ApiHost.hostOf("https://" + PRIMARY + "/vvv/api/a.json"));
        assertEquals(BACKUP, ApiHost.hostOf("https://" + BACKUP + "/vvv/api/a.json"));
        assertNull(ApiHost.hostOf("https://example.com/x"));
    }

    /**
     * 相似但不同的域名不能被误改。
     * 例如 api.ok123find.top.evil.com 里含有 PRIMARY 子串，替换后会变成
     * api.ok124find.top.evil.com —— 仍然是恶意域名，但更关键的是要确认我们
     * 不会把它当成自家域名去 markWorking。
     */
    @Test
    public void similarDomainsAreNotConfused() {
        assertNull(ApiHost.hostOf("https://api.ok123find.top.example.com/x"));
    }

    /** 两个常量必须是两个不同的真实域名，避免改成同一个之后切换变空操作。 */
    @Test
    public void primaryAndBackupDiffer() {
        assertEquals("api.ok123find.top", PRIMARY);
        assertEquals("api.ok124find.top", BACKUP);
    }
}
