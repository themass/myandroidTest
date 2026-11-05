package com.openapi.data.vpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.openapi.data.remote.ProxyLinkSelector;
import com.openapi.data.remote.ShareLinkParser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ProxyFallbackChainTest {

    private static ProxyLinkSelector.Candidate candidate(String protocol, String link) throws Exception {
        return new ProxyLinkSelector.Candidate(protocol, link, ShareLinkParser.parse(link));
    }

    private static List<ProxyLinkSelector.Candidate> threeCandidates() throws Exception {
        List<ProxyLinkSelector.Candidate> list = new ArrayList<>();
        list.add(candidate("trojan", "trojan://pw@a.example.com:8443?sni=a.example.com#a"));
        list.add(candidate("hysteria2", "hysteria2://pw@b.example.com:8444?sni=b.example.com#b"));
        list.add(candidate("shadowsocks",
                "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNz@c.example.com:8388#c"));
        return list;
    }

    @Test
    public void triesCandidatesInOrderAndStopsAtTheEnd() throws Exception {
        List<String> tried = new ArrayList<>();
        ProxyFallbackChain chain = new ProxyFallbackChain(threeCandidates(),
                c -> tried.add(c.protocol));
        chain.connectNext();
        chain.connectNext();
        chain.connectNext();
        assertEquals(java.util.Arrays.asList("trojan", "hysteria2", "shadowsocks"), tried);
        assertFalse(chain.hasNext());
        chain.connectNext();
        assertEquals(3, tried.size());
    }

    @Test
    public void hasNextReflectsRemainingCandidates() throws Exception {
        ProxyFallbackChain chain = new ProxyFallbackChain(threeCandidates(), c -> { });
        assertTrue(chain.hasNext());
        chain.connectNext();
        assertEquals("trojan", chain.current().protocol);
        assertTrue(chain.hasNext());
        chain.connectNext();
        chain.connectNext();
        assertFalse(chain.hasNext());
        assertEquals("shadowsocks", chain.current().protocol);
    }

    @Test
    public void currentIsNullBeforeStartAndAfterExhaustion() throws Exception {
        ProxyFallbackChain chain = new ProxyFallbackChain(threeCandidates(), c -> { });
        assertNull(chain.current());
        chain.connectNext();
        chain.connectNext();
        chain.connectNext();
        chain.connectNext();
        assertEquals("shadowsocks", chain.current().protocol);
    }

    @Test
    public void emptyChainHasNothingToTry() throws Exception {
        List<String> tried = new ArrayList<>();
        ProxyFallbackChain chain = new ProxyFallbackChain(new ArrayList<>(), c -> tried.add(c.protocol));
        assertFalse(chain.hasNext());
        chain.connectNext();
        assertTrue(tried.isEmpty());
        assertEquals(0, chain.size());
    }
}
