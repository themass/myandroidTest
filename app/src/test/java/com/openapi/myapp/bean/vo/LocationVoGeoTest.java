package com.openapi.myapp.bean.vo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;

import org.junit.Test;

/**
 * 服务端下发节点经纬度的解析契约。
 * <p>
 * 这条路径出过问题：location_v2 的 lat/lng 列加好、SQL 也回填了，但地球上一块砖都没动。
 * 原因不在客户端——服务端没部署，响应里根本没有这两个字段。为了以后能一眼分清
 * "服务端没下发" 和 "客户端没解析"，把字段名与容错行为钉成测试。
 */
public class LocationVoGeoTest {

    private static final Gson GSON = new Gson();

    @Test
    public void parsesLatLngFromServerJson() {
        String json = "{\"id\":16,\"name\":\"香港\",\"ename\":\"HK\",\"lat\":22.3193,\"lng\":114.1694}";
        LocationVo vo = GSON.fromJson(json, LocationVo.class);
        assertEquals(16, vo.id);
        assertEquals(22.3193, vo.lat, 1e-6);
        assertEquals(114.1694, vo.lng, 1e-6);
    }

    @Test
    public void missingFieldsStayNullSoFallbackCanKickIn() {
        // 旧服务端的响应：没有 lat/lng。必须解析成 null 而不是 0——
        // 0,0 是几内亚湾，会被当成"服务端给了坐标"，节点全堆到海里
        String json = "{\"id\":3,\"name\":\"法国\",\"ename\":\"France\"}";
        LocationVo vo = GSON.fromJson(json, LocationVo.class);
        assertNull(vo.lat);
        assertNull(vo.lng);
    }

    @Test
    public void integerCoordinatesStillParse() {
        // MySQL DECIMAL 有时会序列化成整数字面量（如 0、51），不能因此解析失败
        String json = "{\"id\":1,\"lat\":51,\"lng\":0}";
        LocationVo vo = GSON.fromJson(json, LocationVo.class);
        assertEquals(51.0, vo.lat, 1e-6);
        assertEquals(0.0, vo.lng, 1e-6);
    }

    @Test
    public void nullExplicitIsTreatedAsAbsent() {
        String json = "{\"id\":1,\"lat\":null,\"lng\":null}";
        LocationVo vo = GSON.fromJson(json, LocationVo.class);
        assertNull(vo.lat);
        assertNull(vo.lng);
    }
}
