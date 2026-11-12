package com.openapi.data.vpn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.openapi.ks.free1.R;

import org.junit.Test;

/**
 * 磁贴外观映射。
 * <p>
 * 判定顺序必须与雷达按钮、通知栏一致（先看错误，再看连接状态）。三处一旦分叉，
 * 同一个时刻会显示三种说法；而磁贴是最容易被误读的一个——用户看到"未连接"再点一次，
 * 实际是第二次触发连接。
 */
public class VpnTileServiceUiTest {

    private static VpnTileService.Ui ui(boolean err, boolean conn, boolean connecting, boolean disconnecting) {
        return VpnTileService.uiFor(err, conn, connecting, disconnecting);
    }

    @Test
    public void idleShowsAppNameAndInactive() {
        VpnTileService.Ui u = ui(false, false, false, false);
        assertEquals(R.string.app_name, u.labelRes);
        assertFalse(u.active);
    }

    @Test
    public void connectedIsActive() {
        VpnTileService.Ui u = ui(false, true, false, false);
        assertEquals(R.string.vpn_bg_conned, u.labelRes);
        assertTrue(u.active);
    }

    @Test
    public void connectingStaysActiveSoUserCanCancel() {
        // 连接中必须保持可点：再点一下应该是"取消"，而不是变灰点不动
        VpnTileService.Ui u = ui(false, false, true, false);
        assertEquals(R.string.vpn_bg_connecting_short, u.labelRes);
        assertTrue(u.active);
    }

    @Test
    public void disconnectingIsInactive() {
        VpnTileService.Ui u = ui(false, false, false, true);
        assertEquals(R.string.vpn_bg_disconnecting, u.labelRes);
        assertFalse(u.active);
    }

    @Test
    public void errorWinsOverEverythingElse() {
        // 出错时哪怕状态还是 CONNECTED，也必须显示错误——否则用户以为连上了
        assertFalse(ui(true, true, false, false).active);
        assertEquals(R.string.vpn_bg_error, ui(true, true, false, false).labelRes);
        assertEquals(R.string.vpn_bg_error, ui(true, false, false, true).labelRes);
    }

    @Test
    public void connectingTakesPrecedenceOverDisconnecting() {
        // 极端场景（快速连点）：连接状态优先，避免显示"断开中"却其实正在连
        VpnTileService.Ui u = ui(false, false, true, true);
        assertTrue(u.active);
        assertEquals(R.string.vpn_bg_connecting_short, u.labelRes);
    }
}
