package com.timeline.vpn.data;

import android.content.Context;

import com.timeline.vpn.R;
import com.timeline.vpn.bean.vo.LocationVo;
import com.timeline.vpn.common.util.PreferenceUtils;
import com.timeline.vpn.common.util.SystemUtils;
import com.timeline.vpn.constant.Constants;

import static com.kuaiyou.util.a.getActivity;

/**
 * Created by themass on 2017/1/13.
 */

public class LocationUtil {
    public static String getSelectName(Context context) {
        LocationVo vo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        String name = vo == null ? context.getString(R.string.location_choose_none) : (SystemUtils.isZH(context) ? vo.name : vo.ename);
        return context.getString(R.string.location_choose_hint) + name;
    }

    public static int getSelectId(Context context) {
        LocationVo chooseVo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        return chooseVo == null ? 0 : chooseVo.id;
    }

    public static void setLocation(Context context, Object data) {
        PreferenceUtils.setPrefObj(getActivity(), Constants.LOCATION_CHOOSE, data);
    }
}