package com.openapi.myapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.openapi.myapp.bean.vo.LocationVo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NodeShortcutsTest {

    private static List<LocationVo> nodes(int... ids) {
        List<LocationVo> list = new ArrayList<>();
        for (int id : ids) {
            LocationVo vo = new LocationVo();
            vo.id = id;
            list.add(vo);
        }
        return list;
    }

    private static List<Integer> ids(List<LocationVo> list) {
        List<Integer> out = new ArrayList<>();
        for (LocationVo vo : list) {
            out.add(vo.id);
        }
        return out;
    }

    @Test
    public void favoritesFirstThenRecentThenRest() {
        Set<Integer> fav = new LinkedHashSet<>(Arrays.asList(5, 2));
        List<Integer> recent = Arrays.asList(4, 1);
        List<LocationVo> out = NodeShortcuts.arrange(nodes(1, 2, 3, 4, 5, 6), fav, recent);
        // 收藏保持原列表里的相对顺序（2 在 5 前），最近按最近到最早，其余原样
        assertEquals(Arrays.asList(2, 5, 4, 1, 3, 6), ids(out));
    }

    @Test
    public void nodeBothFavoriteAndRecentAppearsOnce() {
        Set<Integer> fav = new LinkedHashSet<>(Collections.singletonList(3));
        List<LocationVo> out = NodeShortcuts.arrange(nodes(1, 2, 3), fav, Arrays.asList(3, 1));
        assertEquals(Arrays.asList(3, 1, 2), ids(out));
    }

    @Test
    public void staleIdsAreIgnoredAndNothingIsLost() {
        // 99 是已下线的节点：不应出现，也不应让别的节点丢失
        Set<Integer> fav = new LinkedHashSet<>(Collections.singletonList(99));
        List<LocationVo> out = NodeShortcuts.arrange(nodes(1, 2, 3), fav, Arrays.asList(98, 2));
        assertEquals(Arrays.asList(2, 1, 3), ids(out));
        assertEquals(3, out.size());
    }

    @Test
    public void pushRecentDedupesAndTruncates() {
        List<Integer> r = new ArrayList<>();
        for (int id : new int[]{1, 2, 3, 4}) {
            r = NodeShortcuts.pushRecent(r, id);
        }
        assertEquals(Arrays.asList(4, 3, 2), r);                 // 只留 3 个
        assertEquals(Arrays.asList(2, 4, 3), NodeShortcuts.pushRecent(r, 2));   // 已有的移到最前
    }

    @Test
    public void toggleDoesNotMutateInput() {
        Set<Integer> fav = new LinkedHashSet<>(Collections.singletonList(1));
        Set<Integer> added = NodeShortcuts.toggle(fav, 2);
        assertTrue(added.contains(2));
        assertFalse(fav.contains(2));
        assertFalse(NodeShortcuts.toggle(added, 2).contains(2));
    }

    @Test
    public void parseSkipsGarbageButKeepsTheRest() {
        assertEquals(new LinkedHashSet<>(Arrays.asList(1, 3)), NodeShortcuts.parseSet("1,abc,,3"));
        assertTrue(NodeShortcuts.parseSet(null).isEmpty());
        assertEquals("4,5", NodeShortcuts.join(Arrays.asList(4, 5)));
    }
}
