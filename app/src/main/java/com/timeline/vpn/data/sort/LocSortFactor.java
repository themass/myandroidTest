package com.timeline.vpn.data.sort;

import com.qq.e.comm.util.StringUtil;
import com.timeline.vpn.bean.vo.LocationVo;
import com.timeline.vpn.constant.Constants;

import java.util.Comparator;

/**
 * Created by themass on 2016/8/17.
 */
public class LocSortFactor implements Comparator<LocationVo> {
    private String sortFactor;

    public LocSortFactor(String sortFactor) {
        this.sortFactor = sortFactor;
    }

    @Override
    public int compare(LocationVo lhs, LocationVo rhs) {
        if (StringUtil.isEmpty(sortFactor)) {
            return 0;
        }
        String[] sortArr = sortFactor.split("_");
        if (sortArr.length != 2) {
            return 0;
        }
        String a;
        String b;
        if (Constants.sortType.equals(sortArr[0])) {
            a = String.valueOf(lhs.type);
            b = String.valueOf(rhs.type);
        } else if (Constants.sortCountry.equals(sortArr[0])) {
            a = lhs.ename;
            b = rhs.ename;
        } else {
            a = String.valueOf(lhs.level);
            b = String.valueOf(rhs.level);
        }
        if (Constants.sortASC.equals(sortArr[1])) {
            return ase(a, b);
        } else {
            return desc(a, b);
        }
    }

    private int ase(String a, String b) {
        return a.compareTo(b);
    }

    private int desc(String a, String b) {
        return b.compareTo(a);
    }
}
