package com.qq.vpn.support;

import android.content.Context;

import com.qq.ext.util.PreferenceUtils;
import com.qq.ext.util.SystemUtils;
import com.qq.network.R;
import com.qq.vpn.domain.res.LocationVo;
import com.qq.Constants;

/**
 * Created by dengt on 2017/1/13.
 */

public class LocationUtil {
    public static String getSelectName(Context context) {
        LocationVo vo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        String name = vo == null ? context.getString(R.string.location_select_none) : (SystemUtils.isZH(context) ? vo.name : vo.ename);
        return context.getString(R.string.menu_btn_country) + name;
    }
    public static String getSelectLocationIcon(Context context) {
        LocationVo vo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        String url = vo == null ? Constants.LOCATION_ICON_ALL : vo.img;
        return url;
    }
    public static String getName(Context context) {
        LocationVo vo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        if(vo==null){
            return Constants.DEFULT_LOCATION_NAME;
        }
        return vo.name;
    }
    public static int getSelectLocationId(Context context) {
        LocationVo chooseVo = PreferenceUtils.getPrefObj(context, Constants.LOCATION_CHOOSE, LocationVo.class);
        return chooseVo == null ? 0 : chooseVo.id;
    }
    public static void setLocation(Context context, Object data) {
        PreferenceUtils.setPrefObj(context, Constants.LOCATION_CHOOSE, data);
    }
}
