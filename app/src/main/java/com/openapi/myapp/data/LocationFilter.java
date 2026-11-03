package com.openapi.myapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.openapi.myapp.bean.vo.LocationVo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 节点列表的本地排序与“Best / Auto”选择。
 * <p>
 * 节点数据是 {@code /location/vip/cache.json} 一次性下发的全量列表，排序在本地完成。
 */
public final class LocationFilter {

    private LocationFilter() {
    }

    /**
     * 延迟升序：有效延迟（> 0）在前，无数据或失败（null / -1）在后；同延迟保持原顺序。
     * 只用于“Best / Auto”的选择，不改变列表本身的展示顺序。
     */
    @Nullable
    public static LocationVo bestOf(@NonNull List<LocationVo> source) {
        LocationVo best = null;
        for (LocationVo vo : source) {
            if (!hasValidPing(vo)) {
                continue;
            }
            if (best == null || vo.ping < best.ping) {
                best = vo;
            }
        }
        return best;
    }

    /** 把有效延迟的节点按延迟排到前面，其余保持原顺序。 */
    @NonNull
    public static List<LocationVo> sortByPing(@NonNull List<LocationVo> source) {
        List<LocationVo> copy = new ArrayList<>(source);
        Collections.sort(copy, new Comparator<LocationVo>() {
            @Override
            public int compare(LocationVo a, LocationVo b) {
                boolean va = hasValidPing(a);
                boolean vb = hasValidPing(b);
                if (va && vb) {
                    return Integer.compare(a.ping, b.ping);
                }
                if (va) {
                    return -1;
                }
                if (vb) {
                    return 1;
                }
                return 0;
            }
        });
        return copy;
    }

    private static boolean hasValidPing(@NonNull LocationVo vo) {
        return vo.ping != null && vo.ping > 0;
    }
}
